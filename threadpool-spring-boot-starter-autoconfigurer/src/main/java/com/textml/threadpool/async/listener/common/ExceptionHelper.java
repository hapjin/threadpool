package com.textml.threadpool.async.listener.common;

import java.util.List;

/**
 * @author psj
 * @date 2019/06/15
 */
public final class ExceptionHelper {
    public static <T extends Throwable> void maybeThrowRuntimeExceptionAndSuppress(List<T> exceptions) {
        T main = null;
        for (T exception : exceptions) {
            //to do
        }
    }
}
