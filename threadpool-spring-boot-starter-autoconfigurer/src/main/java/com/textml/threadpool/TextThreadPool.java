package com.textml.threadpool;

import com.textml.threadpool.exception.TextAbortPolicy;
import com.textml.threadpool.exception.TextForceQueuePolicy;
import com.textml.threadpool.exception.TextRejectExecutionHandler;
import com.textml.threadpool.queue.TextExecutorScalingQueue;
import com.textml.threadpool.queue.TextSizeBlockingQueue;
import com.textml.threadpool.schedule.TextScheduler;
import com.textml.threadpool.util.TextTimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * @author psj
 * @date 2019/03/12
 * <p>
 * 各种线程池的管理类,各种类型的线程池创建、访问都从这个类开始
 * <p>
 * 线程池有两个属性: 一个是名称,由 TextThreadPool.Names 定义
 * 另一个是: 类型
 */
public class TextThreadPool implements TextScheduler {

    private static final Logger logger = LoggerFactory.getLogger(TextThreadPool.class);

    /**
     * 定义 执行操作的 名称, 会为每个操作创建一个线程池,线程的名字以操作名称命名,不同的操作可以使用同一种类型的线程池
     */
    public static class Names {
        /**
         * 在调用者线程中执行的操作
         */
        public static final String SAME = "same";
        /**
         * 普通操作(一次至少16个线程)
         */
        public static final String GENERIC = "generic";

        /**
         * 一般程序执行的读写操作/或者一些周期性任务的操作(4个固定的线程)
         */
        public static final String WRITE = "write";
        public static final String READ = "read";

        /**
         * IO 密集型 CPU 密集型任务的线程池(线程个数动态调整)
         * IO:  [1,availableProcessors * 2]
         * CPU: [1,availableProcessors +1]
         */
        public static final String IO = "io";
        public static final String CPU = "cpu";

        /**
         * 只有一个线程 的线程池,当任务提交到这个线程池时,由单个线程逐个处理任务,从而不需要并发/同步
         */
        public static final String SINGLE = "single";

    }

    /**
     * 线程池类型,主要以 创建线程池的 任务队列的不同来表示不同的线程池,其次则是线程池采用的拒绝策略的不同
     * 向线程池提交任务被拒绝而“强制”再次提交任务 和 线程池中的线程在执行任务过程中抛出异常再次重新拉起任务 是不同的
     * <p>
     * 需要注意的是,所有类型的线程池在线程执行任务过程中若出现了异常,只要将异常向上抛出,并且 isForce 设置为true
     * 那么就能够重新提交任务,具体实现参考 {@link TextThreadPoolExecutor#afterExecute(java.lang.Runnable, java.lang.Throwable)}
     * <p>
     * 而向线程池提交任务时被拒绝了,被拒绝的任务如何处理,请参考创建线程池时指定的拒绝策略是什么
     */
    public enum ThreadPoolType {
        /**
         *
         */
        DIRECT("direct"),
        /**
         * 线程池任务队列的长度固定不变,采用的任务队列是{@link TextSizeBlockingQueue}
         * 采用的拒绝策略是 {@link TextAbortPolicy}, 能够支持当任务被拒绝时,强制提交执行(isForce设置为 true)
         */
        FIXED("fixed"),
        /**
         * 线程池任务队列的长度可动态调整
         */
        FIXED_AUTO_QUEUE_SIZE("fixed_auto_queue_size"),
        /**
         * 该线程池 优先创建线程数量到 max pool size 来处理任务. 任务队列是{@link TextExecutorScalingQueue}
         * 采用的拒绝策略是 {@link TextForceQueuePolicy},
         */
        SCALING("scaling");

        private final String type;

        ThreadPoolType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }


        private static final Map<String, ThreadPoolType> TYPE_MAP;

        static {
            Map<String, ThreadPoolType> typeMap = new HashMap<>();
            for (ThreadPoolType threadPoolType : ThreadPoolType.values()) {
                typeMap.put(threadPoolType.getType(), threadPoolType);
            }
            TYPE_MAP = Collections.unmodifiableMap(typeMap);
        }

        public static ThreadPoolType fromType(String type) {
            ThreadPoolType threadPoolType = TYPE_MAP.get(type);
            if (threadPoolType == null) {
                throw new IllegalArgumentException("no ThreadPoolType for " + type);
            }
            return threadPoolType;
        }
    }

    public static final Map<String, ThreadPoolType> THREAD_POOL_TYPES;

    static {
        HashMap<String, ThreadPoolType> map = new HashMap<>();
        map.put(Names.GENERIC, ThreadPoolType.SCALING);
        map.put(Names.SAME, ThreadPoolType.DIRECT);

        THREAD_POOL_TYPES = Collections.unmodifiableMap(map);
    }


    private final ExecutorService DIRECT_EXECUTOR = TextExecutors.newDirectExecutorService();

    /**
     * 执行定时任务的任务池
     */
    private final ScheduledThreadPoolExecutor scheduler;


    public Collection<ExecutorBuilder> builders() {
        return Collections.unmodifiableCollection(builders.values());
    }

    private Map<String, ExecutorHolder> executors;
    private final Map<String, ExecutorBuilder> builders;

    /**
     * 使用线程池构造器 创建线程池
     *
     * @param customBuilders 自定义的线程池构造器
     */
    public TextThreadPool(final ExecutorBuilder<?>... customBuilders) {
        final Map<String, ExecutorBuilder> builders = new HashMap<>();
        final int availableProcessors = TextExecutors.NUMBER_OF_PROCESSORS;
        //创建一个线程名称为GENERIC,无界任务队列(支持: 优先创建线程到max pool size 执行任务)
        //core pool size=4, max pool size=availableProcessors
        builders.put(Names.GENERIC, new ScalingExecutorBuilder(Names.GENERIC, 4,
                availableProcessors, TextTimeValue.timeValueSeconds(30)));

        //创建一个线程名称为READ,任务队列长度固定为200,(core pool size==max pool size)线程数量为4的线程池
        builders.put(Names.READ, new FixedExecutorBuilder(Names.READ, 4, 200));
        builders.put(Names.WRITE, new FixedExecutorBuilder(Names.WRITE, 4, 200));

        //IO/CPU, Scaling类型的线程池任务队列是无界队列
        builders.put(Names.IO, new ScalingExecutorBuilder(Names.IO, 1, availableProcessors * 2, TextTimeValue.timeValueSeconds(60 * 60)));
        builders.put(Names.CPU, new ScalingExecutorBuilder(Names.CPU, 1, availableProcessors + 1, TextTimeValue.timeValueSeconds(60 * 60)));

        //创建一个线程池名称为single,任务队列长度固定为200, (core pool size==max pool size)线程数量为1的线程池
        builders.put(Names.SINGLE, new FixedExecutorBuilder(Names.SINGLE, 1, 200));

        //添加自定义的线程池构造器
        for (final ExecutorBuilder<?> builder : customBuilders) {
            if (builders.containsKey(builder.name())) {
                throw new IllegalArgumentException("builder with name [" + builder.name() + "] already exists");
            }
            builders.put(builder.name(), builder);
        }
        this.builders = Collections.unmodifiableMap(builders);

        //ExecutorHolder 封装了线程池 及 线程池描述信息
        final Map<String, ExecutorHolder> executors = new HashMap<>();
        for (Map.Entry<String, ExecutorBuilder> entry : builders.entrySet()) {
            final ExecutorBuilder.ExecutorSettings settings = entry.getValue().getSettings();
            //使用线程池构建器创建线程池
            final ExecutorHolder executorHolder = entry.getValue().build(settings);

            if (executors.containsKey(executorHolder.info.getName())) {
                throw new IllegalStateException("duplicated thread pool:[ "
                        + entry.getValue().formatInfo(executorHolder.info) + "] registered");
            }
            executors.put(entry.getKey(), executorHolder);
            logger.info("build thread pool:{}", executorHolder.info);
        }

        //添加调用者线程池
        executors.put(Names.SAME, new ExecutorHolder(DIRECT_EXECUTOR, new Info(Names.SAME, ThreadPoolType.DIRECT)));

        this.executors = Collections.unmodifiableMap(executors);
        this.scheduler = TextScheduler.initScheduler();
    }


    /**
     * get ExecutorService with given name.
     *
     * @param name the name of the executor service to obtain
     * @return
     */
    public ExecutorService executor(String name) {
        final ExecutorHolder holder = executors.get(name);
        if (holder == null) {
            throw new IllegalArgumentException("no executor service found for [" + name + "]");
        }
        return holder.executor();
    }

    public ExecutorService generic() {
        //generic 线程池是内置的
        return executor(Names.GENERIC);
    }


    /**
     * 执行 周期性的 定时任务
     * 周期性的定时任务其实就是每隔一段时间执行一次定时任务
     *
     * @param command  任务
     * @param interval 时间间隔
     * @param executor 执行该任务的线程池
     * @return
     */
    @Override
    public Cancellable scheduleWithFixedDelay(Runnable command, TextTimeValue interval, String executor) {
        return new ReschedulingRunnable(command, interval, executor, this,
                (e) -> {
                    logger.error("scheduled task {} was rejected on thread pool {}, exception:{}", command, executor, e);
                },
                (e) -> {
                    logger.error("failed to run scheduled task {} on thread pool {}, exception:{}", command, executor, e);
                }
        );
    }

    /**
     * 执行一个定时任务
     *
     * @param delay        延时启动时间
     * @param executorName 执行该定时任务的线程池
     * @param command      任务
     * @return 结果
     */
    @Override
    public ScheduledFuture<?> schedule(TextTimeValue delay, String executorName, Runnable command) {
        if (!Names.SAME.equals(executorName)) {
            command = new ThreadedRunnable(command, executor(executorName));
        }
        return scheduler.schedule(new TextThreadPool.LoggingRunnable(command), delay.getMillis(), TimeUnit.MILLISECONDS);
    }

    public ScheduledExecutorService scheduler() {
        return this.scheduler;
    }

    /**
     * 封装 线程池 及 描述该线程池相关信息
     */
    public static class ExecutorHolder {
        private final ExecutorService executorService;
        private final Info info;

        public ExecutorHolder(ExecutorService executorService, Info info) {
            this.executorService = executorService;
            this.info = info;
        }

        public ExecutorService executor() {
            return executorService;
        }
    }//end class ExecutorHolder

    /**
     * 封装线程池信息
     */
    public static class Info {
        private final String name;
        private final ThreadPoolType type;
        private final int corePoolSize;
        private final int maxPoolSize;
        private final TextTimeValue keepAlive;

        /**
         * 无界队列 queueSize 默认为 -1
         */
        private final int queueSize;

        public Info(String name, ThreadPoolType type) {
            this(name, type, -1);
        }

        public Info(String name, ThreadPoolType type, int size) {
            this(name, type, size, size, null, size);
        }

        public Info(String name, ThreadPoolType type, int min, int max, TextTimeValue keepAlive, int queueSize) {
            this.name = name;
            this.type = type;
            this.corePoolSize = min;
            this.maxPoolSize = max;
            this.keepAlive = keepAlive;
            this.queueSize = queueSize;
        }

        public String getName() {
            return name;
        }

        public ThreadPoolType getType() {
            return type;
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public TextTimeValue getKeepAlive() {
            return keepAlive;
        }

        public int getQueueSize() {
            return queueSize;
        }


        /**
         * core pool size == max pool size 时, keep alive =-1
         * @return
         */
        @Override
        public String toString() {
            return String.format("name: %s, type: %s, core pool size: %d max pool size: %d, keepAlive: %d seconds, queue size:%d",
                    name, type, corePoolSize, maxPoolSize, keepAlive == null ? -1 : keepAlive.getSeconds(), queueSize);
        }
    }// end class Info

    public TextThreadPoolInfo info() {
        List<Info> infos = new ArrayList<>();
        for (ExecutorHolder holder : executors.values()) {
            String name = holder.info.getName();
            //no need to have info on "same" thread pool
            if ("same".equals(name)) {
                continue;
            }

            infos.add(holder.info);
        }
        return new TextThreadPoolInfo(infos);
    }

    public Info info(String name) {
        ExecutorHolder holder = executors.get(name);
        if (holder == null) {
            return null;
        }
        return holder.info;
    }

    public TextThreadPoolStats stats() {
        List<TextThreadPoolStats.Stats> stats = new ArrayList<>();
        for (ExecutorHolder holder : executors.values()) {
            String name = holder.info.getName();
            if ("same".equals(name)) {
                continue;
            }

            int threads,queue,active,largest;
            threads = queue = active = largest = -1;
            long rejected, completed;
            rejected = completed = -1;

            if (holder.executor() instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor executor = (ThreadPoolExecutor) holder.executor();
                threads = executor.getPoolSize();
                queue = executor.getQueue().size();
                active = executor.getActiveCount();
                largest = executor.getLargestPoolSize();
                completed = executor.getCompletedTaskCount();
                RejectedExecutionHandler rejectedExecutionHandler = executor.getRejectedExecutionHandler();
                if (rejectedExecutionHandler instanceof TextRejectExecutionHandler) {
                    rejected = ((TextRejectExecutionHandler) rejectedExecutionHandler).rejected();
                }
            }
            stats.add(new TextThreadPoolStats.Stats(name, threads, queue, active, rejected, largest, completed));
        }

        return new TextThreadPoolStats(stats);
    }


    public static boolean terminate(TextThreadPool pool, long timeout, TimeUnit timeUnit) {
        if (pool != null) {
            try {
                pool.shutdown();
                if (awaitTermination(pool, timeout, timeUnit)) {
                    return true;
                }
                pool.shutdownNow();
                return awaitTermination(pool, timeout, timeUnit);
            } finally {
                //IOutils.close...
            }
        }
        return false;
    }

    public void shutdown() {
        scheduler.shutdown();
        for (ExecutorHolder executor: executors.values()) {
            if (executor.executor() instanceof ThreadPoolExecutor) {
                executor.executor().shutdown();
            }
        }
    }

    public void shutdownNow() {
        scheduler.shutdownNow();
        for (ExecutorHolder executor : executors.values()) {
            if (executor.executor() instanceof ThreadPoolExecutor) {
                executor.executor().shutdownNow();
            }
        }
    }


    private static boolean awaitTermination(final TextThreadPool threadPool, final long timeout, final TimeUnit timeUnit) {
        try {
            if (threadPool.awitTermination(timeout, timeUnit)) {
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

        }
        return false;
    }

    public boolean awitTermination(long timeout, TimeUnit timeUnit)throws InterruptedException {
        boolean result = scheduler.awaitTermination(timeout, timeUnit);
        for (ExecutorHolder executor : executors.values()) {
            if (executor.executor() instanceof ThreadPoolExecutor) {
                result &= executor.executor().awaitTermination(timeout, timeUnit);
            }
        }
        return result;
    }

    /**
     * 正确关闭线程池的步骤
     * 先执行{@link ExecutorService#shutdown()}, 等待一段时间,再执行 {@link ExecutorService#shutdownNow()}
     * 然后超时等待返回线程池是否关闭成功
     *
     * @param service
     * @param timeout
     * @param timeUnit
     * @return 是否关闭成功
     */
    public static boolean terminate(ExecutorService service, long timeout, TimeUnit timeUnit) {
        if (service != null) {
            service.shutdown();

            if (awaitTermination(service, timeout, timeUnit)) {
                return true;
            }

            service.shutdownNow();
            return awaitTermination(service, timeout, timeUnit);
        }
        return false;
    }

    private static boolean awaitTermination(final ExecutorService executorService, final long timeout, final TimeUnit timeUnit) {
        try {
            if (executorService.awaitTermination(timeout, timeUnit)) {
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    class LoggingRunnable implements Runnable {
        private final Runnable runnable;

        public LoggingRunnable(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void run() {
            try {
                //这里的 runnable 一定重写了 java.lang.Runnable 的 run 方法
                runnable.run();
            } catch (Exception e) {
                logger.warn("failed to run {}, exception:{}", runnable.toString(), e.getMessage());
                throw e;
            }
        }

        @Override
        public int hashCode() {
            return runnable.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return runnable.equals(obj);
        }

        @Override
        public String toString() {
            return "[threaded] " + runnable.toString();
        }
    }

    class ThreadedRunnable implements Runnable {
        private final Runnable runnable;
        private final Executor executor;

        public ThreadedRunnable(Runnable runnable, Executor executor) {
            this.runnable = runnable;
            this.executor = executor;
        }

        @Override
        public void run() {
            executor.execute(runnable);
        }

        @Override
        public int hashCode() {
            return runnable.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return runnable.equals(obj);
        }

        @Override
        public String toString() {
            return "[threaded] " + runnable.toString();
        }
    }
}
