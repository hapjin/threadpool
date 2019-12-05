package com.textml.threadpool.async.listener.action;


import java.util.concurrent.Future;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * @author psj
 * @date 2019/06/15
 */
public abstract class BaseFuture<V> implements Future<V> {


    static final class Sync<V> extends AbstractQueuedSynchronizer {
        /**
         * valid states
         */
        static final int RUNNING = 0;
        static final int COMPLETING = 1;
        static final int COMPLETED = 2;
        static final int CANCELLED = 4;

        private V value;
        private Throwable exception;


        @Override
        protected int tryAcquireShared(int arg) {
            return super.tryAcquireShared(arg);
        }
    }

}

