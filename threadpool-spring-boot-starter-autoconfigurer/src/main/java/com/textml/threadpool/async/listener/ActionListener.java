package com.textml.threadpool.async.listener;

import com.textml.threadpool.async.listener.common.CheckedConsumer;
import com.textml.threadpool.async.listener.common.ExceptionHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author psj
 * @date 2019/06/15
 */
public interface ActionListener<Response> {
    void onResponse(Response response);
    void onFailure(Exception e);

    /**
     *
     * @param onResponse 执行完成的处理逻辑
     * @param onFailure 执行失败的处理逻辑
     * @param <Response>
     * @return
     */
    static <Response> ActionListener<Response> wrap(CheckedConsumer<Response, ? extends Exception> onResponse,
                                                    Consumer<Exception> onFailure) {
        return new ActionListener<Response>() {
            @Override
            public void onResponse(Response response) {
                try {
                    // 把 得到的 response 用 onResponse 这个处理逻辑进行处理
                    onResponse.accept(response);
                } catch (Exception e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                onFailure.accept(e);
            }
        };
    }

    /**
     * 实现了Runnable任务的异步处理. 当Runnable执行完成,就会调用onResponse方法,执行失败调用onFailure方法
     * @param runnable
     * @param <Response>
     * @return
     */
    static <Response> ActionListener<Response> wrap(Runnable runnable) {
        return wrap(r -> runnable.run(), r -> runnable.run());
    }


    /**
     * ActionListener的本质是什么? 是一个处理逻辑,是怎么对Response进行处理的 一个处理逻辑
     * @param listeners 一系列的处理逻辑
     * @param response 干完某件事后的一个结果
     * @param <Response> 产生了一个什么类型的结果
     */
    static <Response> void onResponse(Iterable<ActionListener<Response>> listeners, Response response) {
        List<Exception> exceptionList = new ArrayList<>();
        for (ActionListener<Response> listener : listeners) {
            try {
                //处理逻辑 在处理 结果 时也可能抛出异常
                listener.onResponse(response);
            } catch (Exception ex) {
                try {
                    listener.onFailure(ex);
                } catch (Exception ex1) {
                    exceptionList.add(ex1);
                }
            }
        }
        ExceptionHelper.maybeThrowRuntimeExceptionAndSuppress(exceptionList);
    }
}
