package com.textml.threadpool.exception;

import com.textml.threadpool.queue.TextExecutorScalingQueue;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author psj
 * @date 2019/03/12
 *
 * 拒绝策略,与{@link TextExecutorScalingQueue}搭配使用,当
 * 因为 TextExecutorScalingQueue 是一个无界的任务队列(LinkedTransferQueue)
 * TextSizeBlockingQueue 虽然也是LinkedTransferQueue,但是有 capacity 属性限制.
 *
 */
public class TextForceQueuePolicy implements TextRejectExecutionHandler {

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        try {
            //向线程池提交任务被拒绝,如果线程池的任务队列是TextExecutorScalingQueue,则强制将该任务再提交到线程池
            assert executor.getQueue() instanceof TextExecutorScalingQueue;
            executor.getQueue().put(r);
        } catch (final InterruptedException e) {
            //TextExecutorScalingQueue的 put方法 永远不会阻塞,因此这个异常不可能发生
            throw new AssertionError(e);
        }

        //这里没有抛出运行时异常,因此就不会触发 TextThreadPoolExecutor.afterExecute 中的异常处理
    }

    @Override
    public long rejected() {
        return 0;
    }
}
