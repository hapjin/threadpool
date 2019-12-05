package com.textml.threadpool.queue;

import com.textml.threadpool.exception.TextForceQueuePolicy;

import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author psj
 * @date 2019/03/12
 * TextExecutorScalingQueue 是一个无界队列, 没有容量限制,因此 "强制" 将任务插入到该队列时不会出现队列已满的情况
 * TextExecutorScalingQueue 必须与 {@link TextForceQueuePolicy}一起使用
 */
public class TextExecutorScalingQueue<E> extends LinkedTransferQueue<E> {
    ThreadPoolExecutor threadPoolExecutor;

    public TextExecutorScalingQueue() {

    }

    public void setThreadPoolExecutor(ThreadPoolExecutor executor) {
        this.threadPoolExecutor = executor;
    }

    /**
     * LinkedTransferQueue 是无界队列, 重写offer方法 使之能够支持 core pool size 和 max pool size
     * 这样保证线程池优先创建 max pool size 个线程处理任务,后续的任务就入队列等待
     * @param e
     * @return
     */
    @Override
    public boolean offer(E e) {
        //first try to transfer to a waiting worker thread
        //如果线程池中有空闲线程tryTransfer立即成功
        if (!tryTransfer(e)) {
         //检查线程池是否还可以继续创建新线程
            int leftThread = threadPoolExecutor.getMaximumPoolSize() - threadPoolExecutor.getCorePoolSize();
            if (leftThread > 0) {
                //线程池还可以继续创建线程,因此返回false触发新建线程
                //{@see java.util.concurrent.ThreadPoolExecutor.addWorker}
                return false;
            }else {
                return super.offer(e);
            }
        }else {
            return true;
        }
    }
}
