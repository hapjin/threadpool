package com.textml.threadpool.async.listener.common;

/**
 * @author psj
 * @date 2019/06/15
 */
@FunctionalInterface
public interface CheckedConsumer<T, E extends Exception> {
    void accept(T t) throws E;
}
