package com.textml.threadpool.schedule;

import com.textml.threadpool.TextExecutors;
import com.textml.threadpool.runnable.TextAbstractRunnable;
import com.textml.threadpool.exception.TextAbortPolicy;
import com.textml.threadpool.exception.TextRejectExecutionHandler;
import com.textml.threadpool.exception.TextRejectedExecutionException;
import com.textml.threadpool.util.TextTimeValue;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author psj
 * @date 2019/03/12
 *
 * 创建执行定时任务的线程池
 */
public interface TextScheduler {

    /**
     * 创建 定时任务 线程池
     * @return
     */
    static ScheduledThreadPoolExecutor initScheduler() {
        ThreadFactory threadFactory = TextExecutors.daemonThreadFactory("scheduler");
        TextRejectExecutionHandler handler = new TextAbortPolicy();

        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, threadFactory, handler);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    static boolean terminate(ScheduledThreadPoolExecutor executor, long timeout, TimeUnit timeUnit) {
        executor.shutdown();
        if (awaitTermination(executor, timeout, timeUnit)) {
            return true;
        }

        //last resort
        executor.shutdownNow();
        return awaitTermination(executor, timeout, timeUnit);
    }

    static boolean awaitTermination(final ScheduledThreadPoolExecutor executor, final long timeout, final TimeUnit timeUnit) {
        try {
            if (executor.awaitTermination(timeout, timeUnit)) {
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    /**
     * 在其他地方只需要 实现 Runnable command 就可以创建并执行一个定时任务,非常灵活.
     *
     * @param delay
     * @param executor
     * @param command
     * @return
     */
    ScheduledFuture<?> schedule(TextTimeValue delay, String executor, Runnable command);

    default Cancellable scheduleWithFixedDelay(Runnable command, TextTimeValue interval, String executor) {
        return new ReschedulingRunnable(command, interval, executor, this, (e)->{}, (e)->{});
    }


    final class ReschedulingRunnable extends TextAbstractRunnable implements Cancellable {
        private final Runnable runnable;
        private final TextTimeValue interval;
        private final String executor;
        private final TextScheduler scheduler;
        private final Consumer<Exception> rejectionConsumer;
        private final Consumer<Exception> failureConsumer;

        /**
         * run 用 volatile 修饰后,就不需要用final修饰了 ^:)^
         *
         * @see {@link TextScheduler.ReschedulingRunnable#onAfter()}
         */
        private volatile boolean run = true;
        /** ReschedulingRunnable.onAfter 会再次提交执行任务 */
        public ReschedulingRunnable(Runnable runnable, TextTimeValue interval, String executor, TextScheduler scheduler,
                             Consumer<Exception> rejectionConsumer, Consumer<Exception> failureConsumer) {
            this.runnable = runnable;
            this.interval = interval;
            this.executor = executor;
            this.scheduler = scheduler;
            this.rejectionConsumer = rejectionConsumer;
            this.failureConsumer = failureConsumer;

            //
            scheduler.schedule(interval, executor, this);
        }

        /**
         * 取消周期性执行的任务
         */
        @Override
        public void cancel() {
            run = false;
        }

        @Override
        public boolean isCancelled() {
            return run == false;
        }

        @Override
        public void doRun() {
            if (run) {
                //周期性任务在每次执行时,判断它是否已经取消了,从而实现了可取消的周期性任务
                runnable.run();
            }
        }

        @Override
        public void onFailure(Exception t) {
            failureConsumer.accept(t);
        }

        @Override
        public void onRejection(Exception e) {
            run = false;
            rejectionConsumer.accept(e);
        }


        /**
         *
         */
        @Override
        public void onAfter() {
            if (run) {
                try {
                    scheduler.schedule(interval, executor, this);
                } catch (final TextRejectedExecutionException e) {
                    onRejection(e);
                }
            }
        }
    }



    interface Cancellable{
        /**
         * 取消 周期性执行的任务
         */
        void cancel();

        /**
         * 查询任务是否被取消
         * @return
         */
        boolean isCancelled();
    }
}
