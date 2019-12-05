package com.textml.threadpool.exception;

import java.util.concurrent.RejectedExecutionException;

/**
 * @author psj
 * @date 2019/03/07
 *
 * 仅仅是定义了异常,异常的处理参考:
 * 在 {@link TextAbortPolicy#rejectedExecution(java.lang.Runnable, java.util.concurrent.ThreadPoolExecutor)}
 * 里面 new TextRejectedExecutionException异常 并抛出
 *
 */
public class TextRejectedExecutionException extends RejectedExecutionException {
    private final boolean isExecutorShutdown;

    public TextRejectedExecutionException(String message, boolean isExecutorShutdown) {
        super(message);
        this.isExecutorShutdown = isExecutorShutdown;
    }

    public TextRejectedExecutionException(String message) {
        this(message, false);
    }
    public TextRejectedExecutionException() {
        this(null, false);
    }

    /**
     *
     * @return
     */
    public boolean isExecutorShutdown() {
        return isExecutorShutdown;
    }
}
