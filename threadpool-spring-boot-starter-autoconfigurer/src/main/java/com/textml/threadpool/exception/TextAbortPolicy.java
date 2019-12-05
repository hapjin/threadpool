package com.textml.threadpool.exception;

import com.textml.threadpool.runnable.TextAbstractRunnable;
import com.textml.threadpool.queue.TextSizeBlockingQueue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author psj
 * @date 2019/03/11
 *
 * 当提交给线程池的任务被拒绝时自动调用 TextAbortPolicy#rejectedExecution(java.lang.Runnable, java.util.concurrent.ThreadPoolExecutor)
 *
 * 自定义线程池拒绝策略, 对于可强制执行的任务,如果被线程池拒绝策略拒绝了,仍然可以提交.
 *
 */
public class TextAbortPolicy implements TextRejectExecutionHandler {

    private final LongAdder rejected = new LongAdder();


    /**
     * {@link ThreadPoolExecutor#ThreadPoolExecutor}
     * the handler to use when execution is blocked because the thread bounds and queue capacities are reached
     * @param r
     * @param executor
     */
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        if (r instanceof TextAbstractRunnable) {
            if (((TextAbstractRunnable) r).isForceExecution()) {
                BlockingQueue<Runnable> queue = executor.getQueue();

                if (!(queue instanceof TextSizeBlockingQueue)) {
                    throw new IllegalArgumentException("forced execution, but expected a size queue");
                }

                try {
                    ((TextSizeBlockingQueue) queue).forcePut(r);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("force execution, but got interrupted", e);
                }
                return;
            }
        }
        //不是强制执行的任务, 拒绝任务数量加1,抛出拒绝异常
        rejected.increment();

        //
        throw new TextRejectedExecutionException("rejected execution of " + r + " on " + executor, executor.isShutdown());
    }

    @Override
    public long rejected() {
        return rejected.sum();
    }
}
