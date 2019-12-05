package com.textml.threadpool;

import com.textml.threadpool.exception.TextAbortPolicy;
import com.textml.threadpool.exception.TextForceQueuePolicy;
import com.textml.threadpool.exception.TextRejectExecutionHandler;
import com.textml.threadpool.queue.TextExecutorScalingQueue;
import com.textml.threadpool.queue.TextResizableBlockingQueue;
import com.textml.threadpool.queue.TextSizeBlockingQueue;
import com.textml.threadpool.runnable.TextTimedRunnable;
import com.textml.threadpool.util.TextTimeValue;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author psj
 * @date 2019/03/06
 *
 * 创建线程池工具类
 */
public class TextExecutors {
    /**
     * 保证至少16个线程
     */
    public static final int NUMBER_OF_PROCESSORS = Math.max(16, Runtime.getRuntime().availableProcessors());

    /**
     * can not instantiate
     */
    private TextExecutors(){}


    /**
     * 创建 Fixed 类型线程池,线程池的核心线程数等于最大线程数
     * @param name 线程池的名称,用于日志打印
     * @param size core pool size == max pool size
     * @param queueCapacity 任务队列的长度,当queueSize>0时, 此线程池使用的任务队列是一个有界队列,否则是无界队列
     * @param threadFactory 创建线程的线程工厂
     * @return TextThreadPoolExecutor 在 afterExecute 中可支持任务的重新拉起
     */
    public static TextThreadPoolExecutor newFixed(String name, int size, int queueCapacity, ThreadFactory threadFactory) {
        //任务拒绝策略处理器(将任务提交给线程池时如果被拒绝了,如何处理?)
        TextAbortPolicy handler = new TextAbortPolicy();
        //线程池任务队列
        BlockingQueue<Runnable> queue;
        if (queueCapacity < 0) {
            queue = new LinkedTransferQueue<>();
        }else {
            queue = new TextSizeBlockingQueue<>(new LinkedTransferQueue<>(), queueCapacity);
        }
        //Fixed 线程池的任务队列是 TextSizeBlockingQueue,它能够支持 force put (当任务被拒绝时,强制入任务队列)
        return new TextThreadPoolExecutor(name, size, size, 0, TimeUnit.MILLISECONDS, queue, threadFactory, handler);
    }

    /**
     * 创建可扩展(Scaling)的线程池, 通过 tryTransfer 直接获取任务, 任务队列是一个无界任务队列
     * 当线程池数量未达到max pool size时,优先创建线程 {@link TextExecutorScalingQueue#offer(java.lang.Object)}
     * @param name 线程池名称
     * @param min core pool size
     * @param max max pool size
     * @param keepAlive 空闲线程存活时间
     * @param timeUnit 单位
     * @param threadFactory 创建线程的线程工厂
     * @return TextThreadPoolExecutor 在 afterExecute 中可支持任务的重新拉起
     */
    public static TextThreadPoolExecutor newScaling(String name, int min, int max, long keepAlive, TimeUnit timeUnit,
                                                    ThreadFactory threadFactory) {
        TextRejectExecutionHandler handler = new TextForceQueuePolicy();
        //创建一个无界任务队列
        TextExecutorScalingQueue<Runnable> queue = new TextExecutorScalingQueue<>();
        TextThreadPoolExecutor executor = new TextThreadPoolExecutor(name, min, max, keepAlive, timeUnit,
                queue, threadFactory, handler);
        queue.setThreadPoolExecutor(executor);
        return executor;
    }
    /**
     * 创建Auto线程池, 可动态 增加/减少 任务队列的长度
     * @param name 线程池名称
     * @param size 用来执行 Runnable 任务的线程数
     * @param initialQueueCapacity 任务队列的初始容量
     * @param minQueueSize 允许的任务队列的最小容量
     * @param maxQueueSize 允许的任务队列的最大容量
     * @param frameSize 在调整任务队列的长度之前,需要统计的 Runnable 任务数量,(根据统计数据计算调整参数)
     * @param targetedResponseTime Runnable 任务的响应时间
     * @param threadFactory 线程工厂
     * @return TextThreadPoolExecutor 在 afterExecute 中可支持任务的重新拉起
     */
    public static TextThreadPoolExecutor newAutoQueueFixed(String name, int size, int initialQueueCapacity, int minQueueSize,
                                                           int maxQueueSize, int frameSize, TextTimeValue targetedResponseTime,
                                                           ThreadFactory threadFactory) {
        if (initialQueueCapacity <= 0) {
            throw new IllegalArgumentException("initial queue capacity for [" + name + "] executor must be positive, got: " + initialQueueCapacity);
        }

        TextResizableBlockingQueue<Runnable> queue = new TextResizableBlockingQueue<>(new LinkedTransferQueue<>(), initialQueueCapacity);
        TextRejectExecutionHandler handler = new TextAbortPolicy();
        return new ResizingTextThreadPoolExecutor(name, size, size, 0, TimeUnit.MILLISECONDS, queue, minQueueSize, maxQueueSize,
                TextTimedRunnable::new, frameSize, targetedResponseTime, threadFactory, handler);
    }

    /**
     * 创建一个执行带有优先级任务的线程池,该线程池的 core pool size 和 max pool size 都为1
     * 也即:由单线程来执行提交到线程池中的任务, 这样就不需要考虑 多个任务 执行时的并发问题,因此特别适合执行状态更新类的任务
     *
     * 用法:
     * TextThreadPool threadPool = new TextThreadPool();
     *         PrioritizedTextThreadPoolExecutor priThreadPoolExecutor = TextExecutors.newSinglePrioritizing(
     *                 "priority", daemonThreadFactory("update_pri"), threadPool.scheduler()
     *         );
     */
    public static PrioritizedTextThreadPoolExecutor newSinglePrioritizing(String name, ThreadFactory threadFactory,
                                                                          ScheduledExecutorService timer) {
        return new PrioritizedTextThreadPoolExecutor(name, 1, 1, 0L, TimeUnit.MILLISECONDS, threadFactory, timer);
    }

    static class TextThreadFactory implements ThreadFactory {
        final ThreadGroup threadGroup;
        final AtomicInteger threadNumber = new AtomicInteger(1);
        /**
         * 线程池名称,方便日志查看
         */
        final String namePrefix;

        TextThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
            SecurityManager s = System.getSecurityManager();
            threadGroup = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(threadGroup, r, namePrefix + "[T#" + threadNumber.getAndIncrement() + "]");
            t.setDaemon(true);
            return t;
        }
    }

    @Deprecated
    public static ThreadFactory daemonThreadFactory(String threadPoolTypeName, String... names) {
        return new TextThreadFactory(threadName(threadPoolTypeName, names));
    }

    @Deprecated
    public static String threadName(String threadPoolTypeName, String... names) {
        String namePrefix = Arrays.stream(names).filter(name -> name != null)
                .collect(Collectors.joining(".", "[", "]"));
        return threadPoolTypeName + "->" + namePrefix;
    }

    public static ThreadFactory daemonThreadFactory(String namePrefix) {
        return new TextThreadFactory(namePrefix);
    }

    public static ExecutorService newDirectExecutorService() {
        return DIRECT_EXECUTOR_SERVICE;
    }

    /**
     * 实现一个在调用者线程中执行Runnable任务的"线程池"
     */
    private static final ExecutorService DIRECT_EXECUTOR_SERVICE = new AbstractExecutorService() {
        @Override
        public void shutdown() {
            //在调用者线程中执行任务,当然没有所谓的关闭操作了
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Runnable> shutdownNow() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return false;
        }

        /**
         *  直接运行 {@link Runnable#run()},而不是执行 {@link ThreadPoolExecutor#execute(java.lang.Runnable)}提交任务
         * @param command
         */
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };









}
