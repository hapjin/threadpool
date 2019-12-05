package com.textml.threadpool.queue;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author psj
 * @date 2019/03/06
 *
 * 支持对任务队列的长度检查,防止任务队列无限制增长. 虽然指定了任务队列的长度为 capacity ,但是当任务可强制提交时,
 * 任务队列的长度可大于 capacity
 */
public class TextSizeBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E> {

    /**
     * LinkedTransferQueue 的构造方法不支持 指定 长度,因而是无界队列
     *
     * implement note: queue 只用 LinkedTransferQueue 实例化
     */
    private final BlockingQueue<E> queue;

    /**
     * {@link TextSizeBlockingQueue#offer(java.lang.Object)} 向队列中添加元素时执行capacity校验
     *  由于LinkedTransferQueue本身是个无界队列, 因此根据 capacity 检查, 防止任务队列长度过长
     * 同时支持强制向队列中{@link TextSizeBlockingQueue#forcePut(java.lang.Object)}添加元素
     */
    private final int capacity;

    /**
     * queue 中当前元素个数, 如何保证size与queue中元素个数一致?
     */
    private final AtomicInteger size = new AtomicInteger();

    public TextSizeBlockingQueue(BlockingQueue<E> queue, int capacity) {
        assert capacity > 0;
        this.queue = queue;
        this.capacity = capacity;
    }

    /**
     *
     * Inserts the specified element at the tail of this queue. As the queue is unbounded,
     * this method will never block.
     * @param e
     */
    public void forcePut(E e) throws InterruptedException{
        size.incrementAndGet();
        try {
            queue.put(e);
        } catch (InterruptedException ie) {
            size.decrementAndGet();
            throw ie;
        }
    }

    /**
     * {@link LinkedTransferQueue#offer(java.lang.Object, long, java.util.concurrent.TimeUnit)} 是线程安全的
     * 但如何保证{@link TextSizeBlockingQueue#offer(java.lang.Object)} 也是正确的呢? size属性与queue中元素个数保持一致
     * @param e
     * @return
     */
    @Override
    public boolean offer(E e) {
        //why using where true loop? 避免 加锁
        while (true) {
            final int current = size.get();
            //超过当前队列长度,不允许新任务添加到队列
            if (current >= capacity) {
                //
                return false;
            }

            //while true loop 保证 cas 失败了,当前队列长度不超过capacity时,还会再次尝试
            if (size.compareAndSet(current, 1 + current)) {
                break;
            }
        }
        //CAS操作成功了,执行入队列
        //LinkedTransferQueue 的 offer 方法是非阻塞的,添加失败直接返回false
        boolean offered = queue.offer(e);
        if (!offered) {
            //入队列失败, 恢复队列大小
            size.decrementAndGet();
        }
        return offered;
    }



    public int capacity() {
        return capacity;
    }


    @Override
    public int size() {
        return size.get();
    }

    @Override
    public Iterator<E> iterator() {
        final Iterator<E> it = queue.iterator();
        return new Iterator<E>() {
            E current;
            @Override
            public void remove() {
                if (queue.remove(current)) {
                    size.decrementAndGet();
                }
            }

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }
            @Override
            public E next() {
                current = it.next();
                return current;
            }
        };
    }

    @Override
    public E peek() {
        return queue.peek();
    }

    @Override
    public E poll() {
        E e = queue.poll();
        if (e != null) {
            size.decrementAndGet();
        }
        return e;
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException{
        E e = queue.poll(timeout, unit);
        if (e != null) {
            size.decrementAndGet();
        }
        return e;
    }

    @Override
    public boolean remove(Object o) {
        boolean v = queue.remove(o);
        if (v) {
            size.decrementAndGet();
        }
        return v;
    }

    @Override
    public E take() throws InterruptedException{
        E e;
        try {
            e = queue.take();
            size.decrementAndGet();
        } catch (InterruptedException ie) {
            throw ie;
        }
        return e;
    }


    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) {
        throw new IllegalStateException("offer with time out not allowed on text size queue");
    }

    @Override
    public void put(E e) {
        //queue作为线程池的任务队列,ThreadPoolExecutor 用不到这个方法
        throw new IllegalStateException("does not support this operation on text size queue");
    }

    @Override
    public int remainingCapacity() {
        return capacity() - size.get();
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        int v = queue.drainTo(c,maxElements);
        size.addAndGet(-v);
        return v;
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        int v = queue.drainTo(c);
        size.addAndGet(-v);
        return v;
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return (T[]) queue.toArray();
    }

    @Override
    public Object[] toArray() {
//        return super.toArray();// 组合模式, 并且继承BlockingQueue
        return queue.toArray();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return queue.containsAll(c);
    }

    @Override
    public boolean contains(Object o) {
        return queue.contains(o);
    }
}
