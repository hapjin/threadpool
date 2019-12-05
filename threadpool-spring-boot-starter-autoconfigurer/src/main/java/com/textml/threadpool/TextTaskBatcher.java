package com.textml.threadpool;

import com.textml.threadpool.util.TaskPriority;
import com.textml.threadpool.exception.TextRejectedExecutionException;
import com.textml.threadpool.runnable.SourcePrioritizedRunnable;
import com.textml.threadpool.util.TextTimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author psj
 * @date 2019/08/28
 * 封装了一个能执行优先级任务的线程池 PrioritizedTextThreadPoolExecutor,用于批量执行优先级任务
 *
 * 外部通过 调用 submitTasks 批量提交优先级任务
 * 或者直接通过 {@link PrioritizedTextThreadPoolExecutor#execute(Runnable, TextTimeValue, Runnable)} 提交单个优先级任务
 */
public abstract class TextTaskBatcher {
    private static final Logger logger = LoggerFactory.getLogger(TextTaskBatcher.class);

    private final PrioritizedTextThreadPoolExecutor threadPoolExecutor;
    /**
     * Object 是每批任务的 batching key ,value 则表示这一批待执行的任务
     * HashMap tasksPerBatchingKey 保存 多批任务. 每批任务都有一个 batching key
     */
    final Map<Object, LinkedHashSet<BatchedTask>> tasksPerBatchingKey = new HashMap<>();

    public TextTaskBatcher(PrioritizedTextThreadPoolExecutor threadPoolExecutor) {
        this.threadPoolExecutor = threadPoolExecutor;
    }

    /**
     * 这里是提交批量优先级任务的方法,后面会执行到：threadPoolExecutor.execute,从而使得 {@link TextTaskBatcher.BatchedTask#run()}执行
     * 进而调用 {@link TextTaskBatcher#runIfNotProcessed(TextTaskBatcher.BatchedTask)} 在这里做一些批量调用检查
     * 然后执行run(updateTask.batchingKey, toExecute, tasksSummary);
     * 进而调用 {@link TextTaskBatcher#run(java.lang.Object, java.util.List, java.lang.String)}
     * 从而由自定义实现"批量任务执行器执行任务"
     *
     * @param tasks
     * @param timeout
     * @throws TextRejectedExecutionException
     */
    public void submitTasks(List<? extends BatchedTask> tasks, TextTimeValue timeout) throws TextRejectedExecutionException {
        if (tasks.isEmpty()) {
            return;
        }
        final BatchedTask firstTask = tasks.get(0);
        //同一批任务的 batching key 必须是一样的
        assert tasks.stream().allMatch(t -> t.batchingKey == firstTask.batchingKey);
        //转化成 IdentityHashMap 根据 task identity 检查任务是否重复
        final Map<Object, BatchedTask> tasksIdentity = tasks.stream().collect(Collectors.toMap(
                BatchedTask::getTask, Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException("cannot add duplicate task:" + a);
                }, IdentityHashMap::new
        ));

        synchronized (tasksPerBatchingKey) {
            LinkedHashSet<BatchedTask> existingTasks = tasksPerBatchingKey
                    .computeIfAbsent(firstTask.batchingKey, k -> new LinkedHashSet<>(tasks.size()));

            for (BatchedTask existingTask : existingTasks) {
                BatchedTask duplicateTask = tasksIdentity.get(existingTask.getTask());
                if (duplicateTask != null) {
                    throw new IllegalStateException("task [" + duplicateTask.describeTasks(Collections.singletonList(existingTask))
                            + "] with source [" + duplicateTask.source() + "] is already queued");
                }
            }

            existingTasks.addAll(tasks);
        }

        //将任务提交给线程池 PrioritizedTextThreadPoolExecutor 执行
        //会调用 TextTaskBatcher.BatchedTask.run 执行
        if (timeout != null) {
            threadPoolExecutor.execute(firstTask, timeout, () -> onTimeoutInternal(tasks, timeout));
        } else {
            threadPoolExecutor.execute(firstTask);
        }

    }

    private void onTimeoutInternal(List<? extends BatchedTask> tasks, TextTimeValue timeout) {
        final ArrayList<BatchedTask> toRemove = new ArrayList<>();
        for (BatchedTask task : tasks) {
            if (task.processed.getAndSet(true) == false) {
                logger.warn("task [{}] timed out after [{}]", task.source(), timeout);
                toRemove.add(task);
            }
        }

        if (!toRemove.isEmpty()) {
            BatchedTask firstTask = toRemove.get(0);
            Object batchingKey = firstTask.batchingKey;

            assert tasks.stream().allMatch(t -> t.batchingKey == batchingKey) :
                    "tasks submitted in a batch should share the same batching key: " + tasks;
            synchronized (tasksPerBatchingKey) {
                LinkedHashSet<BatchedTask> existingTasks = tasksPerBatchingKey.get(batchingKey);
                if (existingTasks != null) {
                    existingTasks.removeAll(toRemove);

                    //batchingKey 对应的 这一批任务都没有了,移除batchingKey下对应的这一批任务
                    if (existingTasks.isEmpty()) {
                        tasksPerBatchingKey.remove(batchingKey);
                    }
                }
            }
            onTimeout(toRemove, timeout);
        }
    }

    /**
     * @param tasks   all tasks have the same batching key
     * @param timeout
     */
    protected abstract void onTimeout(List<? extends BatchedTask> tasks, TextTimeValue timeout);

    /**
     * @param batchingKey all tasks have the given batching key
     * @param tasks
     * @param taskSummary
     */
    protected abstract void run(Object batchingKey, List<? extends BatchedTask> tasks, String taskSummary);

    void runIfNotProcessed(BatchedTask updateTask) {
        //如果这个任务已经执行了,那么将不会执行后续到来的具有相同 batching key 的任务. 后续任务想要执行的话,需要使用不同的 batching key
        if (updateTask.processed.get() == false) {
            final List<BatchedTask> toExecute = new ArrayList<>();
            final Map<String, List<BatchedTask>> processTasksBySource = new HashMap<>();
            synchronized (tasksPerBatchingKey) {
                LinkedHashSet<BatchedTask> pending = tasksPerBatchingKey.remove(updateTask.batchingKey);
                if (pending != null) {
                    for (BatchedTask task : pending) {
                        if (task.processed.getAndSet(true) == false) {
                            logger.info("will process {}", task);
                            toExecute.add(task);
                            processTasksBySource.computeIfAbsent(task.source(), s -> new ArrayList<>()).add(task);
                        } else {
                            logger.info("skipping {}, already processd", task);
                        }
                    }
                }
            }

            if (!toExecute.isEmpty()) {
                final String tasksSummary = processTasksBySource.entrySet().stream().map(
                        entry -> {
                            String tasks = updateTask.describeTasks(entry.getValue());
                            return tasks.isEmpty() ? entry.getKey() : entry.getKey() + "[" + tasks + "]";
                        }
                ).reduce((s1, s2) -> s1 + "," + s2).orElse("");
                //
                run(updateTask.batchingKey, toExecute, tasksSummary);
            }
        }
    }


    /**
     * BatchedTask是一个 PrioritizedRunnable, 因此可提交给 PrioritizedTextThreadPoolExecutor 执行
     */
    protected abstract class BatchedTask extends SourcePrioritizedRunnable {
        /**
         * 该任务是否已经被处理了
         */
        protected final AtomicBoolean processed = new AtomicBoolean();

        protected final Object batchingKey;
        /**
         * 为什么用 Object,而不用 Runnable?
         */
        protected final Object task;

        protected BatchedTask(TaskPriority priority, String source, Object batchingKey, Object task) {
            super(priority, source);
            this.batchingKey = batchingKey;
            this.task = task;
        }

        @Override
        public void run() {
            runIfNotProcessed(this);
        }

        @Override
        public String toString() {
            String taskDesc = describeTasks(Collections.singletonList(this));
            if (taskDesc.isEmpty()) {
                return "[" + source + "]";
            } else {
                return "[" + source + "[" + taskDesc + "]]";
            }
        }

        public abstract String describeTasks(List<? extends BatchedTask> tasks);

        public Object getTask() {
            return task;
        }
    }//end BatchedTask class
}

