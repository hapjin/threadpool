package com.textml.threadpool.runnable;

import com.textml.threadpool.util.TaskPriority;

/**
 * @author psj
 * @date 2019/08/28
 */
public abstract class SourcePrioritizedRunnable extends PrioritizedRunnable {
    protected final String source;

    public SourcePrioritizedRunnable(TaskPriority taskPriority, String source) {
        super(taskPriority);
        this.source = source;
    }

    public String source() {
        return source;
    }

    @Override
    public String toString() {
        return "[" + source + "]";
    }
}
