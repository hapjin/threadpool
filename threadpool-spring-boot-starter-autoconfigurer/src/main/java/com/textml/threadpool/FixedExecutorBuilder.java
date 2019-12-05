package com.textml.threadpool;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * @author psj
 * @date 2019/03/13
 */
public class FixedExecutorBuilder extends ExecutorBuilder<FixedExecutorBuilder.FixedExecutorSettings> {

    private final FixedExecutorSettings settings;

    public FixedExecutorBuilder(String name, int size, int queueSize) {
        super(name);
        settings = new FixedExecutorSettings(size, queueSize);
    }

    @Override
    public TextThreadPool.ExecutorHolder build(FixedExecutorSettings settings) {
        int size = settings.size;
        int queueSize = settings.queueSize;

        final ThreadFactory threadFactory = TextExecutors.daemonThreadFactory(name);

        //queueSize>0时是队列的容量(所允许的最大长度),queueSize<0则是无界队列, size是max pool size==core pool size
        final ExecutorService executor = TextExecutors.newFixed(name, size, queueSize, threadFactory);

        final TextThreadPool.Info info = new TextThreadPool.Info(name, TextThreadPool.ThreadPoolType.FIXED, size, size,
                null, queueSize);

        return new TextThreadPool.ExecutorHolder(executor, info);
    }

    @Override
    public FixedExecutorSettings getSettings() {
        return settings;
    }

    @Override
    public String formatInfo(TextThreadPool.Info info) {
        return String.format(Locale.ROOT, "name [%s], size [%d], queue size [%s]", info.getName(), info.getMaxPoolSize(),
                info.getQueueSize() == -1 ? "unbound" : String.valueOf(info.getQueueSize()));
    }

    static final class FixedExecutorSettings extends ExecutorBuilder.ExecutorSettings {
        private final int size;
        private final int queueSize;

        public FixedExecutorSettings(int size, int queueSize) {
            this.size = size;
            this.queueSize = queueSize;
        }
    }
}
