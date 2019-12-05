package com.textml.threadpool;

/**
 * @author psj
 * @date 2019/03/13
 */
public abstract class ExecutorBuilder<U extends ExecutorBuilder.ExecutorSettings> {

    /**
     *
     */
    protected final String name;

    public ExecutorBuilder(String name) {
        this.name = name;
    }

    protected String name() {
        return name;
    }

    /**
     * 创建线程池
     * @param settings
     * @return
     */
    public abstract TextThreadPool.ExecutorHolder build(U settings);

    /**
     * 格式化线程池描述信息
     * @param info
     * @return
     */
    public abstract String formatInfo(TextThreadPool.Info info);


    abstract U getSettings();

    abstract static class ExecutorSettings{

    }


}
