package com.textml.threadpool.runnable;

import com.textml.threadpool.TextThreadPoolExecutor;
import com.textml.threadpool.schedule.TextScheduler;

/**
 * @author psj
 * @date 2019/03/07
 *
 * 封装Runnable, 提供任务在执行异常或者被拒绝时进行额外的处理工作
 * 任务在执行完成时通过onAfter执行额外的处理工作
 */
public abstract class TextAbstractRunnable implements Runnable {

    /**
     * 判断此任务 是否 强制 提交(针对某些任务队列)
     * 执行过程中出现了异常,并且异常在 onFailure方法中抛出了,并且 force execution 设置为true 则会重新提交任务
     *
     * @return
     */
    public boolean isForceExecution() {
        return false;
    }

    /** ThreadPoolExecute 会执行 TextAbstractRunnable run() */
    @Override
    public void run() {
        try {
            doRun();
            //把 TextAbstractRunnable 执行过程中所有的异常都catch住,由 onFailure 统一处理
        } catch (Exception t) {
            //如果在 onFailure 中往上抛出异常,则会被 TextThreadPoolExecutor.afterExecute 处理(是否重新提交任务)
            onFailure(t);
        }finally {
            onAfter();
        }
    }

    /**
     * 抛出异常时执行,既可能是任务拒绝异常也可能是任务运行时自定义异常
     * @param t
     */
    public abstract void onFailure(Exception t);


    public void onRejection(Exception e) {
        onFailure(e);
    }
    /**
     * 执行完成 或者 被拒绝后 执行
     * 参看 {@link TextThreadPoolExecutor#doExecute(java.lang.Runnable)} catch处理逻辑
     *
     * 比如说定时任务, 就可以在onAfter里面重新再次执行Runnable,从而实现了周期性执行{@link TextScheduler.ReschedulingRunnable#onAfter()}
     *
     */
    public void onAfter(){
        //nothing by default
    }

    /**
     *
     * @throws Exception
     */
    public abstract void doRun() throws Exception;
}