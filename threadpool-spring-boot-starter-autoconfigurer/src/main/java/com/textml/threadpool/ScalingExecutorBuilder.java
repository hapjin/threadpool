package com.textml.threadpool;

import com.textml.threadpool.util.TextTimeValue;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author psj
 * @date 2019/03/13
 */
public final class ScalingExecutorBuilder extends ExecutorBuilder<ScalingExecutorBuilder.ScalingExecutorSettings> {

    private final ScalingExecutorSettings settings;

    public ScalingExecutorBuilder(final String name, final int core, final int max, final TextTimeValue keepAlive) {
        super(name);
        settings = new ScalingExecutorSettings(core, max, keepAlive);
    }


    @Override
    public TextThreadPool.ExecutorHolder build(ScalingExecutorSettings settings) {
        TextTimeValue keepAlive = settings.keeplive;
        int core = settings.core;
        int max = settings.max;

        final ThreadFactory threadFactory = TextExecutors.daemonThreadFactory(name);

        //创建 Scaling 线程池
        final ExecutorService executor = TextExecutors.newScaling(name, core, max, keepAlive.getMillis(),
                TimeUnit.MILLISECONDS, threadFactory);

        //线程池描述信息(Scaling类型的线程池任务队列是无界队列,因此queueSize=-1)
        final TextThreadPool.Info info = new TextThreadPool.Info(name, TextThreadPool.ThreadPoolType.SCALING, core, max,
                keepAlive, -1);

        return new TextThreadPool.ExecutorHolder(executor, info);
    }

    @Override
    public String formatInfo(TextThreadPool.Info info) {
        return String.format(Locale.ROOT, "name [%s], core [%d], max [%d], keep alive [%s]", info.getName(),
                info.getCorePoolSize(), info.getMaxPoolSize(), info.getKeepAlive());
    }

    @Override
    public ScalingExecutorSettings getSettings() {
        return settings;
    }

    /**
     * Scaling 类型的线程池 配置描述
     */
    public final static class ScalingExecutorSettings extends ExecutorBuilder.ExecutorSettings {
        private final int core;
        private final int max;
        private final TextTimeValue keeplive;

        public ScalingExecutorSettings(int core, int max, TextTimeValue keeplive) {
            this.core = core;
            this.max = max;
            this.keeplive = keeplive;
        }
    }
}

