package com.textml.threadpool.exception;

import java.util.concurrent.RejectedExecutionHandler;

/**
 * @author psj
 * @date 2019/03/07
 */
public interface TextRejectExecutionHandler extends RejectedExecutionHandler {
    long rejected();
}
