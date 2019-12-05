package com.textml.threadpool.async.listener.action;

import com.textml.threadpool.util.TextTimeValue;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author psj
 * @date 2019/06/15
 *
 * 有2种风格的异步调用, 一种是 ActionFuture, 另一种是 ActionListener
 * 由 Client 负责执行
 */
public interface ActionFuture<T> extends Future<T> {

    /**
     * 与 {@link Future#get()}一样, 但是捕获InterruptionException,抛出IllegalStateException
     * @return
     */
    T actionGet();

    T actionGet(String timeout);

    T actionGet(long timeoutMillis);

    T actionGet(long timeout, TimeUnit unit);

    T actionGet(TextTimeValue timeout);
}
