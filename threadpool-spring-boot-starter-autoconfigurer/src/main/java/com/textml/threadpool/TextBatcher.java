package com.textml.threadpool;

import com.textml.threadpool.util.TaskPriority;
import com.textml.threadpool.util.TextTimeValue;

import java.util.ArrayList;
import java.util.List;

/**
 * @author psj
 * @date 2019/08/29
 * 提供了一个执行批量优先级任务的实现类, 若有其他 执行逻辑 需求, 继承TaskBatcher即可
 * 一个具体的 "批量任务执行器"
 */
public class TextBatcher extends TextTaskBatcher {

    TextBatcher(PrioritizedTextThreadPoolExecutor threadPoolExecutor) {
        super(threadPoolExecutor);
    }

    @Override
    protected void onTimeout(List<? extends BatchedTask> tasks, TextTimeValue timeout) {

    }

    @Override
    protected void run(Object batchingKey, List<? extends BatchedTask> tasks, String taskSummary) {

    }


    class UpdateTask extends BatchedTask{
        /**
         *
         * @param priority
         * @param source
         * @param batchingKey 可以是其他类型的对象,不一定是Object
         * @param task
         */
        UpdateTask(TaskPriority priority, String source, Object batchingKey, Object task) {
            super(priority, source, batchingKey, task);
        }

        @Override
        public String describeTasks(List<? extends BatchedTask> tasks) {
            return null;
        }
    }


    public static void main(String[] args) {
        TextThreadPool threadPool = new TextThreadPool();
        PrioritizedTextThreadPoolExecutor threadPoolExecutor = TextExecutors
                .newSinglePrioritizing("test", TextExecutors.daemonThreadFactory("priority"), threadPool.scheduler());

        TextBatcher textBatcher = new TextBatcher(threadPoolExecutor);
        List<UpdateTask> updateTasks = textBatcher.testForCreateUpdateTask();
        textBatcher.submitTasks(updateTasks, TextTimeValue.timeValueSeconds(5));
    }

    public List<UpdateTask> testForCreateUpdateTask() {
        //for test
        UpdateTask updateTask = new UpdateTask(TaskPriority.NORMAL, "source", "batching_key", "task");
        List<UpdateTask> need2ExecutedBatchTasks = new ArrayList<>();
        need2ExecutedBatchTasks.add(updateTask);
        return need2ExecutedBatchTasks;
    }
}
