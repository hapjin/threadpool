package com.textml.threadpool.runnable;


/**
 * @author psj
 * @date 2019/03/11
 *
 * 封装Runnable,记录Runnable任务的创建时间,启动时间,完成时间
 */
public class TextTimedRunnable extends TextAbstractRunnable {
    private final Runnable original;

    /**
     * 在创建对象的时候就有初始值,所以用final修饰
     */
    private final long creationTimeNanos;

    /**
     * 这两个属性没有用final修饰,因为这两个属性是在任务启动的时候才能赋值
     */
    private long startTimeNanos;
    private long finishTimeNanos = -1;


    public TextTimedRunnable(final Runnable original) {
        this.original = original;
        creationTimeNanos = System.nanoTime();
    }

    @Override
    public void doRun() {
        try {
            startTimeNanos = System.nanoTime();
            original.run();
        }finally {
            //任务执行中途失败, finishTimeNanos 也不是 -1 了
            finishTimeNanos = System.nanoTime();
        }
    }

    @Override
    public void onAfter() {
        if (original instanceof TextAbstractRunnable) {
            //调用父类的处理逻辑
            ((TextAbstractRunnable) original).onAfter();
        }
    }

    @Override
    public void onRejection(Exception e) {
        if (original instanceof TextAbstractRunnable) {
            //调用父类的处理逻辑
            ((TextAbstractRunnable) original).onRejection(e);
        }
    }

    @Override
    public void onFailure(Exception t) {
        if (original instanceof TextAbstractRunnable) {
            //调用父类的处理逻辑
            ((TextAbstractRunnable) original).onFailure(t);
        }
    }

    @Override
    public boolean isForceExecution() {
        return original instanceof TextAbstractRunnable && ((TextAbstractRunnable) original).isForceExecution();
    }

    /**
     * 任务执行失败也是有时间的
     * @return 任务从创建到执行完成的时间(包括任务在队列中排队等待时间), 若任务正在运行或者尚未启动, 则返回-1
     */
    public long getTotalNanos() {
        if (finishTimeNanos == -1) {
            return -1;
        }

        return Math.max(finishTimeNanos - creationTimeNanos, 1);
    }

    /**
     *
     * @return 任务的执行时间(不包括排队等待时间)
     */
    public long getTotalExecutionNanos() {
        if (startTimeNanos == -1 || finishTimeNanos == -1) {
            //there must have been an exception thrown, the total time is unknown (-1)
            return -1;
        }
        return Math.max(finishTimeNanos - startTimeNanos, 1);
    }
}
