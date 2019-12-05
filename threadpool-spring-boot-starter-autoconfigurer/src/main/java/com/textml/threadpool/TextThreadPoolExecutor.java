package com.textml.threadpool;

import com.textml.threadpool.exception.TextAbortPolicy;
import com.textml.threadpool.exception.TextRejectExecutionHandler;
import com.textml.threadpool.exception.TextRejectedExecutionException;
import com.textml.threadpool.queue.TextSizeBlockingQueue;
import com.textml.threadpool.runnable.TextAbstractRunnable;
import com.textml.threadpool.runnable.TextTimedRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author psj
 * @date 2019/03/07
 * <p>
 * afterExecute 支持任务的重新拉起
 */
public class TextThreadPoolExecutor extends ThreadPoolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(TextThreadPoolExecutor.class);
    private volatile ShutdownListener listener;
    private final Object monitor = new Object();

    /**
     * 用来 记录错误日志
     */
    private final String name;

    /**
     * 优先级 任务线程池
     */
    TextThreadPoolExecutor(String name, int corePoolSize, int maximumPoolSize, long keepAlive, TimeUnit unit,
                           BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        this(name, corePoolSize, maximumPoolSize, keepAlive, unit, workQueue, threadFactory, new TextAbortPolicy());
    }

    TextThreadPoolExecutor(String name, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                           BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, TextRejectExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        this.name = name;
    }

    final String getName() {
        return name;
    }

    /**
     * 线程池关闭时回调接口
     */
    public interface ShutdownListener {
        void onTerminated();
    }

    @Override
    protected synchronized void terminated() {
        super.terminated();
        synchronized (monitor) {
            if (listener != null) {
                try {
                    //线程池关闭时可以在onTerminated()做一些处理
                    listener.onTerminated();
                } finally {
                    listener = null;
                }
            }
        }
    }

    @Override
    public void execute(final Runnable command) {
        doExecute(wrapRunnable(command));
    }

    protected void doExecute(final Runnable runnable) {
        try {
            super.execute(runnable);
        } catch (TextRejectedExecutionException e) {
            //任务被拒绝
            if (runnable instanceof TextAbstractRunnable) {
                try {
                    //拒绝处理逻辑
                    ((TextAbstractRunnable) runnable).onRejection(e);
                } finally {
                    //
                    ((TextAbstractRunnable) runnable).onAfter();
                }
            } else {
                throw e;
            }
        }
    }

    /**
     * 扩展ThreadPoolExecutor, Runnable任务完成后调用
     * 因为已经在 {@link TextThreadPoolExecutor#doExecute(java.lang.Runnable)} 实现了任务的 正常/异常 处理
     * 所以这里就只简单地记录日志
     *
     * @param r
     * @param t
     */
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        if (t != null) {
            //执行过程中出现了异常,并且异常在 onFailure方法中抛出了,并且 force execution 设置为true
            if (r instanceof TextAbstractRunnable && ((TextAbstractRunnable) r).isForceExecution()) {
                //TextAbstractRunnable 设置为强制执行时重新拉起任务
                execute(r);
                logger.error("TextAbstractRunnable task run time error restarted,msg:{},cause:{}", t.getMessage(), t.getCause());
            }
        }

        //将任务的执行时间记录到日志文件中
        if (r instanceof TextTimedRunnable) {
            final long taskNanos = ((TextTimedRunnable) r).getTotalNanos();
            final long taskExecutionNanos = ((TextTimedRunnable) r).getTotalExecutionNanos();
//            logger.info("total time:{} ns, execution time:{} ns", taskNanos, taskExecutionNanos);
        }

    }
        protected Runnable wrapRunnable (Runnable command){
            return command;
        }

        protected Runnable unwrap (Runnable runnable){
            return runnable;
        }

        @Override
        public String toString () {
            StringBuilder sb = new StringBuilder();
            sb.append(getClass().getSimpleName()).append('[');
            sb.append("name = ").append(name).append(", ");
            if (getQueue() instanceof TextSizeBlockingQueue) {
                TextSizeBlockingQueue queue = (TextSizeBlockingQueue) getQueue();
                sb.append("TextSizeBlockingQueue capacity = ").append(queue.capacity()).append(", ");
            }
            appendThreadPoolExecutorDetails(sb);
            sb.append(super.toString()).append(']');
            return sb.toString();
        }

        /**
         * append details about this thread pool to the specified {@link StringBuilder}.
         * All details should be appended as key/value pairs in the form "%s=%s"
         * @param sb the {@link StringBuilder} to append to
         */
        protected void appendThreadPoolExecutorDetails (StringBuilder sb){

        }
    }
