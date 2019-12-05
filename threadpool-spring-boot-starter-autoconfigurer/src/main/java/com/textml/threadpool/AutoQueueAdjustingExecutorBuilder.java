package com.textml.threadpool;

import com.textml.threadpool.queue.TextResizableBlockingQueue;
import com.textml.threadpool.util.TextTimeValue;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * @author psj
 * @date 2019/03/13
 */
public final class AutoQueueAdjustingExecutorBuilder extends ExecutorBuilder<AutoQueueAdjustingExecutorBuilder.AutoExecutorSettings> {

    /**
     * 任务的响应时间 默认为 1s, 这个值会影响任务队列的长度
     */
    private static final TextTimeValue targetResponsedTime = TextTimeValue.timeValueSeconds(1);

    private final AutoExecutorSettings settings;

    public AutoQueueAdjustingExecutorBuilder(String name, int size, int initialQueueSize, int minQueueSize, int maxQueueSize,
                                             int frameSize) {
        super(name);
        settings = new AutoExecutorSettings(size, initialQueueSize, minQueueSize, maxQueueSize, frameSize, targetResponsedTime);
    }

    @Override
    public TextThreadPool.ExecutorHolder build(AutoExecutorSettings settings) {
        int size = settings.size;
        int initialQueueSize = settings.initialQueueSize;
        int minQueueSize = settings.minQueueSize;
        int maxQueueSize = settings.maxQueueSize;
        int frameSize = settings.frameSize;
        TextTimeValue targetedResponseTime = settings.targetedResponseTime;

        final ThreadFactory threadFactory = TextExecutors.daemonThreadFactory(name);

        //创建线程池
        final ExecutorService executor = TextExecutors.newAutoQueueFixed(name, size, initialQueueSize, minQueueSize,
                maxQueueSize, frameSize, targetedResponseTime, threadFactory);

        //该线程池的描述信息
        final TextThreadPool.Info info = new TextThreadPool.Info(name(), TextThreadPool.ThreadPoolType.FIXED_AUTO_QUEUE_SIZE,
                size, size, null, initialQueueSize);

        return new TextThreadPool.ExecutorHolder(executor, info);
    }

    @Override
    public AutoExecutorSettings getSettings() {
        return settings;
    }

    /**
     * {@link TextResizableBlockingQueue} 相关的描述信息
     */
    static final class AutoExecutorSettings extends ExecutorBuilder.ExecutorSettings {
        private final int size;
        private final int initialQueueSize;
        private final int minQueueSize;
        private final int maxQueueSize;
        private final int frameSize;
        private final TextTimeValue targetedResponseTime;

        public AutoExecutorSettings(int size, int initialQueueSize, int minQueueSize, int maxQueueSize,
                                    int frameSize, TextTimeValue targetedResponseTime) {
            this.size = size;
            this.initialQueueSize = initialQueueSize;
            this.minQueueSize = minQueueSize;
            this.maxQueueSize = maxQueueSize;
            this.frameSize = frameSize;
            this.targetedResponseTime = targetedResponseTime;
        }

    }

    @Override
    public String formatInfo(TextThreadPool.Info info) {
        return String.format(Locale.ROOT, "name [%s], size [%d], queue size [%s]", info.getName(),
                info.getMaxPoolSize(), info.getQueueSize() == -1 ? "unbound" : String.valueOf(info.getQueueSize()));
    }

}
