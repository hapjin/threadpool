package com.textml.threadpool;

import com.textml.threadpool.exception.TextRejectExecutionHandler;
import com.textml.threadpool.runnable.TextTimedRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author psj
 * @date 2019.11.14
 *
 * 将每个任务的执行时间上报 metrics 监控的线程池
 */
public class MonitorTextThreadPoolExecutor extends TextThreadPoolExecutor {
    private static final Logger logger = LoggerFactory.getLogger(MonitorTextThreadPoolExecutor.class);

    public MonitorTextThreadPoolExecutor(String name, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, TextRejectExecutionHandler handler) {
        super(name, corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }


    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        if (r instanceof TextTimedRunnable) {
            final long taskNanos = ((TextTimedRunnable) r).getTotalNanos();
            final long taskExecutionNanos = ((TextTimedRunnable) r).getTotalExecutionNanos();
            logger.info("total time:{} ns, execution time:{} ns", taskNanos, taskExecutionNanos);
        }
    }
}
