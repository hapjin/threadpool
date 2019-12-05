package com.textml.threadpool;

import com.textml.threadpool.util.ExponentiallyWeightedMovingAverage;
import com.textml.threadpool.exception.TextRejectExecutionHandler;
import com.textml.threadpool.queue.TextResizableBlockingQueue;
import com.textml.threadpool.runnable.TextTimedRunnable;
import com.textml.threadpool.util.TextTimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * @author psj
 * @date 2019/03/07
 */
public class ResizingTextThreadPoolExecutor extends TextThreadPoolExecutor {
    public static final Logger logger = LoggerFactory.getLogger(ResizingTextThreadPoolExecutor.class);

    public static final double EWMA_ALPHA = 0.3;
    private static final int QUEUE_ADJUSTMENT_SIZE = 50;

    private final Function<Runnable, Runnable> runnableWrapper;
    private final TextResizableBlockingQueue<Runnable> workQueue;

    /**
     * 当已处理的任务达到 taskPerFrame 个(作为一批), 重新计算一次队列的长度
     */
    private final int taskPerFrame;

    private final int minQueueSize;
    private final int maxQueueSize;

    /**
     * The target_response_time is a time value setting that
     * indicates the targeted average response time for tasks in the thread pool queue.
     *
     * 默认为 1s
     */
    private final long targetedResponseTimeNanos;
    private final ExponentiallyWeightedMovingAverage executionEWMA;

    private final AtomicLong totalTaskNanos = new AtomicLong(0);
    private final AtomicInteger taskCount = new AtomicInteger(0);

    /**
     * 创建 ResizingTextThreadPoolExecutor 的时间点
     * 从这个时间点开始,可能有TextTimedRunnable任务继续地提交给ResizingTextThreadPoolExecutor
     *
     * 思考为什么 startNs 没有用 volatile 修饰?
     */
    private long startNs;

    ResizingTextThreadPoolExecutor(String name, int corePoolSize, int maximumPoolSize, long keepAlive, TimeUnit unit,
                                   TextResizableBlockingQueue<Runnable> workQueue, int minQueueSize, int maxQueueSize,
                                   Function<Runnable,Runnable> runnableWrapper, final int taskPerFrame,
                                   TextTimeValue targetedResponseTime, ThreadFactory threadFactory, TextRejectExecutionHandler handler) {
        super(name, corePoolSize, maximumPoolSize, keepAlive, unit, workQueue, threadFactory, handler);
        this.runnableWrapper = runnableWrapper;
        this.workQueue = workQueue;
        this.taskPerFrame = taskPerFrame;
        this.startNs = System.nanoTime();
        this.minQueueSize = minQueueSize;
        this.maxQueueSize = maxQueueSize;
        this.targetedResponseTimeNanos = targetedResponseTime.getNanos();
        this.executionEWMA = new ExponentiallyWeightedMovingAverage(EWMA_ALPHA, 0);
        logger.debug("thread pool [{}] will adjust queue by [{}] when determining automatic queue size", getName(),
                QUEUE_ADJUSTMENT_SIZE);
    }


    /**
     * 当提交任务时,任务不一定立即启动,有可能还在任务队列中排队等待
     * @param runnable
     */
    @Override
    protected void doExecute(Runnable runnable) {
        super.doExecute(this.runnableWrapper.apply(runnable));
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        /**
         * total time is a combination of the time in the queue and time spent running the task.
         *
         */
        assert r instanceof TextTimedRunnable : "expected only TextTimedRunnable in queue";
        if (!(r instanceof TextTimedRunnable)) {
            //throw exception?
            logger.error("expected only TextTimedRunnable in queue");
        }

        //当前任务的完成时间(finishTimeNanos - creationTimeNanos)
        final long taskNanos = ((TextTimedRunnable) r).getTotalNanos();
        final long totalNanos = totalTaskNanos.addAndGet(taskNanos);

        //当前任务的执行时间(finishTimeNanos - startTimeNanos)
        final long taskExecutionNanos = ((TextTimedRunnable) r).getTotalExecutionNanos();
        assert taskExecutionNanos >= 0 : "expected task to always take longer than 0 nanoseconds,got:" + taskExecutionNanos;

        executionEWMA.addValue(taskExecutionNanos);

        if (taskCount.incrementAndGet() == this.taskPerFrame) {
            final long endTimeNs = System.nanoTime();
            final long totalRuntime = endTimeNs - this.startNs;

            //update
            startNs = endTimeNs;

            //calculate the new desired queue size
            try {
                final double lambda = calculateLambda(taskPerFrame, Math.max(totalNanos, 1L));
                final int desiredQueueSize = calculateL(lambda, targetedResponseTimeNanos);
                final int oldCapacity = workQueue.capacity();

                //每次队列长度调整量 QUEUE_ADJUSTMENT_SIZE, 默认为 50
                final int newCapacity = workQueue.adjustCapacity(desiredQueueSize, QUEUE_ADJUSTMENT_SIZE, minQueueSize, maxQueueSize);

                if (oldCapacity != newCapacity) {
                    logger.debug("adjusted [{}] queue size by [{}], old capacity [{}], new capacity [{}]", getName(),
                            newCapacity > oldCapacity ? QUEUE_ADJUSTMENT_SIZE : -QUEUE_ADJUSTMENT_SIZE, oldCapacity, newCapacity);
                }
            } catch (ArithmeticException e) {
                //there was an integer overflow, so just log it, rather than adjust the queue size
                logger.error("failed to calculate optimal queue size for [{}] thread pool, total frame time [{}ns], " +
                        "tasks [{}], task execution time [{}ns]", getName(), totalRuntime, taskPerFrame, totalNanos);

            }finally {
                int tasks = taskCount.addAndGet(-this.taskPerFrame);
                assert tasks >= 0 : "tasks should never be negative, got: " + tasks;

                if (tasks >= this.taskPerFrame) {
                    logger.debug("[{}]: too many incoming tasks while queue size adjustment occurs, " +
                            "resetting measurement to 0", getName());
                    totalTaskNanos.getAndSet(1);
                    taskCount.getAndSet(0);
                    startNs = System.nanoTime();
                }else {
                    //do a regular adjustment
                    totalTaskNanos.addAndGet(-totalNanos);
                }
            }
        }
    }

    /**
     * calculate task rate lambda, for a fixed number of tasks and time it took those tasks to be measured
     * @param totalNumberOfTasks the number of tasks that were measured
     * @param totalFrameTaskNanos 一批任务(taskPerFrame个)总的完成时间(finishTimeNanos - creationTimeNanos)
     * @return the rate of tasks in the system
     */
    static double calculateLambda(final int totalNumberOfTasks, final long totalFrameTaskNanos) {
        //lambda = total tasks divided by measurement time
        assert totalFrameTaskNanos > 0 : "can not calculate for instantaneous tasks,got:" + totalFrameTaskNanos;
        assert totalNumberOfTasks > 0 : "can not calculate for no tasks,got:" + totalNumberOfTasks;

        //lambda = total tasks divided by measurement time
        return (double) totalNumberOfTasks / totalFrameTaskNanos;
    }
    /**
     * calculate little's law, which is optimal queue size for a particular task rate and target response time
     * @param lambda arrival rate of tasks in nanoseconds
     * @param targetedResponseTimeNanos average targeted response rate of requests
     * @return
     */
    static int calculateL(final double lambda, final long targetedResponseTimeNanos) {
        assert targetedResponseTimeNanos > 0 : "can not calculate L for instantaneous request";
        //L = lambda * W
        return Math.toIntExact((long) lambda * targetedResponseTimeNanos);
    }


    /**
     * Returns the exponentially weighted moving average of the task execution time
     * @return 权重
     */
    public double getTaskExecutionEWMA() {
        return executionEWMA.getAverage();
    }

    public int getCurrentCapacity() {
        return workQueue.capacity();
    }

    public int getCurrentQueueSize() {
        return workQueue.size();
    }

    @Override
    protected void appendThreadPoolExecutorDetails(StringBuilder sb) {
        sb.append("min queue capacity = ").append(minQueueSize).append(", ");
        sb.append("max queue capacity = ").append(maxQueueSize).append(", ");
        sb.append("frame size = ").append(taskPerFrame).append(", ");
        sb.append("targeted response rate = ").append(TextTimeValue.timeValueNanos(targetedResponseTimeNanos)).append(", ");
        sb.append("task execution EWMA = ").append(TextTimeValue.timeValueNanos((long) executionEWMA.getAverage())).append(", ");
        sb.append("adjustment amount = ").append(QUEUE_ADJUSTMENT_SIZE).append(", ");
    }
}
