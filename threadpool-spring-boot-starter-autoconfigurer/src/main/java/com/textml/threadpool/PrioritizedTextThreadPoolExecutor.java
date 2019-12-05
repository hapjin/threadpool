package com.textml.threadpool;


import com.textml.threadpool.util.TaskPriority;
import com.textml.threadpool.runnable.PrioritizedCallable;
import com.textml.threadpool.runnable.PrioritizedRunnable;
import com.textml.threadpool.util.FutureUtils;
import com.textml.threadpool.util.TextTimeValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author psj
 * @date 2019/08/28
 *
 * 能够执行优先级任务的线程池,提交给线程池的任务类型可以是 PrioritizedRunnable 或者是 PrioritizedCallable
 * PrioritizedCallable 任务其实是通过 PrioritizedFutureTask#PrioritizedFutureTask(PrioritizedCallable, long)封装实现的
 */
public class PrioritizedTextThreadPoolExecutor extends TextThreadPoolExecutor {

    private static final TextTimeValue NO_WAIT_TIME_VALUE = TextTimeValue.timeValueMillis(0);

    /**记录提交到这个线程池的所有任务的顺序*/
    private final AtomicLong insertionOrder = new AtomicLong();

    private final Queue<Runnable> current = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService timer;

    PrioritizedTextThreadPoolExecutor(String name, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                                      ThreadFactory threadFactory, ScheduledExecutorService timer) {

        super(name, corePoolSize, maximumPoolSize, keepAliveTime, unit, new PriorityBlockingQueue<>(), threadFactory);
        this.timer = timer;
    }

    public Pending[] getPending() {
        List<Pending> pending = new ArrayList<>();
        //current 和 workQueue中的任务 状态应该是不一样的, 但是它们都是尚未执行完的任务
        addPending(new ArrayList<>(current), pending, true);
        addPending(new ArrayList<>(getQueue()), pending, false);
        return pending.toArray(new Pending[pending.size()]);
    }

    public int getNumberOfPendingTasks() {
        int size = current.size();
        size += getQueue().size();
        return size;
    }

    /**
     * @return 返回任务队列中 第一个任务 的等待时间(最长等待时间)
     */
    public TextTimeValue getMaxTaskWaitTime() {
        if (getQueue().size() == 0) {
            return NO_WAIT_TIME_VALUE;
        }

        long now = System.nanoTime();
        long oldestCreationDateInNanos = now;
        for (Runnable queuedRunnable : getQueue()) {
            if (queuedRunnable instanceof PrioritizedRunnable) {
                oldestCreationDateInNanos = Math.min(oldestCreationDateInNanos,
                        ((PrioritizedRunnable) queuedRunnable).getCreationDateInNanos());
            }
        }
        return TextTimeValue.timeValueNanos(now - oldestCreationDateInNanos);
    }



    private void addPending(List<Runnable> runnables, List<Pending> pending, boolean executing) {
        for (Runnable runnable : runnables) {
            if (runnable instanceof TieBreakingPrioritizedRunnable) {
                TieBreakingPrioritizedRunnable t = (TieBreakingPrioritizedRunnable) runnable;
                Runnable innerRunnable = t.runnable;
                if (innerRunnable != null) {
                    //innerRunnable can be null if task is finished but not removed from executor yet
                    pending.add(new Pending(unwrap(innerRunnable), t.priority(), t.insertionOrder, executing));

                }
            } else if (runnable instanceof PrioritizedFutureTask) {
                //FutureTask implements Runnable
                //异步带返回结果任务
                PrioritizedFutureTask t = (PrioritizedFutureTask) runnable;
                Object task = t.task;
                if (t.task instanceof Runnable) {
                    //wrap unwrap 只是为了以后扩展用
                    task = unwrap((Runnable) t.task);
                }
                pending.add(new Pending(task, t.priority, t.insertionOrder, executing));
            }
        }
    }

    /**
     * {@link java.util.concurrent.ThreadPoolExecutor#runWorker}
     */
    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        current.add(r);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        current.remove(r);
    }


    /**
     * 定义线程池 提交任务执行方法,这里并没有直接 重写 {@link java.util.concurrent.ThreadPoolExecutor#execute(java.lang.Runnable)}
     * 主要是为了实现自定义的 线程池 执行任务时 的处理逻辑, 比如任务执行过程中的异常处理,统一后处理逻辑
     *
     * @param command
     * @param timeout
     * @param timeoutCallback
     */
    public void execute(Runnable command, final TextTimeValue timeout, final Runnable timeoutCallback) {
        //把 Runnable 包装成 具有 优先级性质且能够 的任务 且能够 延时执行的任务(TieBreakingPrioritizedRunnable.scheduleTimeout)
        command = wrapRunnable(command);
        doExecute(command);
        if (timeout.nanos() >= 0) {
            if (command instanceof TieBreakingPrioritizedRunnable) {

                ((TieBreakingPrioritizedRunnable) command).scheduleTimeout(timer, timeoutCallback, timeout);
            }else {
                throw new UnsupportedOperationException("Execute with timeout is not supported for future tasks");
            }
        }
    }


    @Override
    protected Runnable wrapRunnable(Runnable command) {
        if (command instanceof PrioritizedRunnable) {
            if (command instanceof TieBreakingPrioritizedRunnable) {
                return command;
            }

            TaskPriority priority = ((PrioritizedRunnable) command).priority();
            return new TieBreakingPrioritizedRunnable(super.wrapRunnable(command), priority, insertionOrder.incrementAndGet());
        } else if (command instanceof PrioritizedFutureTask) {
            return command;
        }else {
            // it might be a callable wrapper
            return new TieBreakingPrioritizedRunnable(super.wrapRunnable(command), TaskPriority.NORMAL, insertionOrder.incrementAndGet());
        }
    }

    /**
     * 封装带执行结果的 Runnable 任务
     * @param runnable
     * @param value
     * @param <T>
     * @return
     */
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        if (!(runnable instanceof PrioritizedRunnable)) {
            //封装 具有优先级并且带返回值的 Runnable
            runnable = PrioritizedRunnable.wrap(runnable, TaskPriority.NORMAL);
        }
        TaskPriority priority = ((PrioritizedRunnable) runnable).priority();
        return new PrioritizedFutureTask<>(runnable, priority, value, insertionOrder.incrementAndGet());
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        if (!(callable instanceof PrioritizedCallable)) {
            //封装成具有优先级的Callable任务
            callable = PrioritizedCallable.wrap(callable, TaskPriority.NORMAL);
        }
        return new PrioritizedFutureTask<>((PrioritizedCallable) callable, insertionOrder.incrementAndGet());
    }

    private final class TieBreakingPrioritizedRunnable extends PrioritizedRunnable {
        private Runnable runnable;
        private final long insertionOrder;
        private ScheduledFuture<?> timeoutFuture;
        private boolean started = false;

        TieBreakingPrioritizedRunnable(Runnable runnable, TaskPriority priority, long insertionOrder) {
            super(priority);
            this.runnable = runnable;
            this.insertionOrder = insertionOrder;
        }

        @Override
        public void run() {
            synchronized (this) {
                started = true;
                FutureUtils.cancel(timeoutFuture);
            }
            runAndClean(runnable);
        }

        private void runAndClean(Runnable run) {
            try {
                run.run();
            }finally {
                runnable = null;
                timeoutFuture = null;
            }
        }

        @Override
        public int compareTo(PrioritizedRunnable pr) {
            int res = super.compareTo(pr);
            if (res != 0 || !(pr instanceof TieBreakingPrioritizedRunnable)) {
                return res;
            }
            return insertionOrder < ((TieBreakingPrioritizedRunnable) pr).insertionOrder ? -1 : 1;
        }

        public void scheduleTimeout(ScheduledExecutorService timer, final Runnable timeoutCallback, TextTimeValue timeValue) {
            synchronized (this) {
                if (timeoutFuture != null) {
                    throw new IllegalStateException("scheduleTimeout may only be called once");
                }
                if (started == false) {
                    timeoutFuture = timer.schedule(new Runnable() {
                        @Override
                        public void run() {
                            if (remove(TieBreakingPrioritizedRunnable.this)) {
                                runAndClean(timeoutCallback);
                            }
                        }
                    }, timeValue.nanos(), TimeUnit.NANOSECONDS);
                }
            }
        }
    }//end class


    private final class PrioritizedFutureTask<T> extends FutureTask<T> implements Comparable<PrioritizedFutureTask> {
        final Object task;
        final TaskPriority priority;
        final long insertionOrder;

        /**参考:java.util.concurrent.Executors.RunnableAdapter */
        PrioritizedFutureTask(Runnable runnable, TaskPriority priority, T value, long insertionOrder) {
            super(runnable, value);
            this.task = runnable;
            this.priority = priority;
            this.insertionOrder = insertionOrder;
        }

        PrioritizedFutureTask(PrioritizedCallable<T> callable, long insertionOrder) {
            super(callable);
            this.task = callable;
            this.priority = callable.priority();
            this.insertionOrder = insertionOrder;
        }

        @Override
        public int compareTo(PrioritizedFutureTask pft) {
            int res = priority.compareTo(pft.priority);
            if (res != 0) {
                return res;
            }
            //如果优先级相同,则按任务入队列的时间进行比较
            return insertionOrder < pft.insertionOrder ? -1 : 1;
        }
    }//end class PrioritizedFutureTask

    public static class Pending{
        public final Object task;
        public final TaskPriority priority;
        public final long insertionOrder;
        public final boolean executing;

        public Pending(Object task, TaskPriority priority, long insertionOrder, boolean executing) {
            this.task = task;
            this.priority = priority;
            this.insertionOrder = insertionOrder;
            this.executing = executing;
        }
    }//end class Pending
}
