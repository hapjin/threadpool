package com.textml.threadpool.runnable;

import com.textml.threadpool.util.TaskPriority;

import java.util.concurrent.Callable;

/**
 * @author psj
 * @date 2019/08/28
 *
 * 抽象类,从而不需要直接在 PrioritizedCallable 实现 java.util.concurrent.Callable#call() 方法
 */
public abstract class PrioritizedCallable<T> implements Callable<T>, Comparable<PrioritizedCallable> {
    private final TaskPriority priority;


    protected PrioritizedCallable(TaskPriority priority) {
        this.priority = priority;
    }
    public static <T> PrioritizedCallable<T> wrap(Callable<T> callable, TaskPriority priority) {
        return new Wrapped<>(callable, priority);
    }

    /**
     * 任务的优先级比较
     * @param pc
     * @return
     */
    @Override
    public int compareTo(PrioritizedCallable pc) {
        return priority.compareTo(pc.priority);
    }

    public TaskPriority priority() {
        return priority;
    }

    static class Wrapped<T> extends PrioritizedCallable<T>{
        private final Callable<T> callable;

        private Wrapped(Callable<T> callable, TaskPriority priority) {
            super(priority);
            this.callable = callable;
        }

        @Override
        public T call() throws Exception{
            return callable.call();
        }
    }//end Wrapped class

}
