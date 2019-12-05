package com.textml.threadpool.async.listener.common;

import com.textml.threadpool.async.listener.ActionListener;
import com.textml.threadpool.runnable.TextAbstractRunnable;

/**
 * @author psj
 * @date 2019/06/15
 */
public abstract class ActionRunnable<Response> extends TextAbstractRunnable {
    protected final ActionListener<Response> listener;

    public ActionRunnable(ActionListener<Response> listener) {
        this.listener = listener;
    }


    /**
     * 当 {@link TextAbstractRunnable#doRun()} 执行过程中抛出异常时执行
     * @param t
     */
    @Override
    public void onFailure(Exception t) {
        listener.onFailure(t);
    }
}
