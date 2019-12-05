package com.textml.threadpool.async.listener.action;

import com.textml.threadpool.TextThreadPool;
import com.textml.threadpool.async.listener.ActionListener;
import com.textml.threadpool.runnable.TextAbstractRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

/**
 * @author psj
 * @date 2019/06/15
 *
 * 使用线程池(新开一个线程)执行 Listener 异步回调结果 的处理逻辑.
 * 试想如果回调结果的处理逻辑很重,有阻塞操作,那怎么办? (类似于Netty IO 线程被阻塞)
 */
public final class ThreadedActionListener<Response> implements ActionListener<Response> {
    private static final Logger logger = LoggerFactory.getLogger(ThreadedActionListener.class);

    private final TextThreadPool threadPool;
    private final String executor;
    private final ActionListener<Response> listener;
    private final boolean forceExecution;

    public ThreadedActionListener(TextThreadPool threadPool, String executor,
                                  ActionListener<Response> listener, boolean forceExecution) {
        this.threadPool = threadPool;
        this.executor = executor;
        this.listener = listener;
        this.forceExecution = forceExecution;
    }

    @Override
    public void onResponse(final Response response) {
        threadPool.executor(executor).execute(new TextAbstractRunnable() {
            @Override
            public boolean isForceExecution() {
                return forceExecution;
            }

            @Override
            public void onFailure(Exception t) {
                listener.onFailure(t);
            }

            @Override
            public void doRun() throws Exception {
                //新开线程执行 listener(处理逻辑) 来处理 响应结果
                listener.onResponse(response);
            }
        });
    }

    @Override
    public void onFailure(Exception e) {
        threadPool.executor(executor).execute(new TextAbstractRunnable() {
            @Override
            public boolean isForceExecution() {
                return forceExecution;
            }

            @Override
            public void doRun() throws Exception {
                //新开一个线程 处理 "操作" 执行失败 后的结果
                listener.onFailure(e);
            }

            @Override
            public void onFailure(Exception t) {
                //这里是指: [处理 "操作" 执行失败 后的结果]的过程中 也有可能抛出异常, 这里只是简单地记录下异常
                logger.warn("failed to execute failure callback on [{}]", t.getCause());
            }
        });
    }

    public static class Wrapper {
        private final TextThreadPool threadPool;
        private final boolean threadedListener;

        public Wrapper(TextThreadPool threadPool) {
            this.threadPool = threadPool;
            //如何让阻塞操作的用户逻辑不在IO线程中执行?
            this.threadedListener = true;
        }

        public <Response> ActionListener<Response> wrap(ActionListener<Response> listener) {
            if (threadedListener == false) {
                return listener;
            }
            //if it's a future, the callback is very lightweight so no need to wrap it
            if (listener instanceof Future) {
                return listener;
            }
            //already threaded...
            if (listener instanceof ThreadedActionListener) {
                return listener;
            }

            return new ThreadedActionListener<>(threadPool, "listener", listener, false);
        }
    }
}
