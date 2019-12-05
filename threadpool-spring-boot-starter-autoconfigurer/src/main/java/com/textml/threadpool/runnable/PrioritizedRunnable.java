package com.textml.threadpool.runnable;

import com.textml.threadpool.util.TaskPriority;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * @author psj
 * @date 2019/08/28
 *
 * 带优先级的任务 抽象类,不需要实现 java.lang.Runnable#run()
 */
public abstract class PrioritizedRunnable implements Runnable, Comparable<PrioritizedRunnable> {

    /**
     * 任务的优先级
     */
    private final TaskPriority priority;
    /**
     * 创建任务时的时间戳 纳秒
     */
    private final long creationDate;
    private final LongSupplier relativeTimeProvider;

    /**
     * 封装Runnable, 在内部类 Wrapped 中执行 Wrapped#run()
     * @param runnable
     * @param taskPriority
     * @return
     */
    public static PrioritizedRunnable wrap(Runnable runnable, TaskPriority taskPriority) {
        return new Wrapped(runnable, taskPriority);
    }

    protected PrioritizedRunnable(TaskPriority priority) {
        this(priority, System::nanoTime);
    }

    PrioritizedRunnable(TaskPriority priority, LongSupplier relativeTimeProvider) {
        this.priority = priority;
        this.creationDate = relativeTimeProvider.getAsLong();
        this.relativeTimeProvider = relativeTimeProvider;
    }

    public long getCreationDateInNanos() {
        return creationDate;
    }

    /**
     * 获取任务自创建以来到当前时间 的时间差
     * @return
     */
    public long getAgeInMillis() {
        return TimeUnit.MILLISECONDS.convert(relativeTimeProvider.getAsLong() - creationDate, TimeUnit.NANOSECONDS);
    }


    @Override
    public int compareTo(PrioritizedRunnable pr) {
        return priority.compareTo(pr.priority);
    }

    public TaskPriority priority() {
        return priority;
    }

    static class Wrapped extends PrioritizedRunnable {
        private final Runnable runnable;

        private Wrapped(Runnable runnable, TaskPriority taskPriority) {
            super(taskPriority);
            this.runnable = runnable;
        }

        @Override
        public void run() {
            runnable.run();
        }
    }//end class Wrapped
}
