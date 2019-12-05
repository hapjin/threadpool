package com.textml.threadpool.util;

import java.util.concurrent.Future;

/**
 * @author psj
 * @date 2019/08/28
 */
public class FutureUtils {
    public static boolean cancel(Future<?> toCancel) {
        if (toCancel != null) {
            return toCancel.cancel(false);
        }
        return false;
    }


}
