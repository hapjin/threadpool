package com.textml.threadpool.util;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author psj
 * @date 2019/11/05
 * <p>
 * 工具类,封装 取消线程任务, 每一个想要被取消的任务通过 execute 方法 添加
 *
 * 不要直接使用 Thread#interrupt() 来直接中断线程,而是通过 cancel() 来中断
 */
public class CancellableThreads {
    private final Set<Thread> threads = new HashSet<>();

    private volatile boolean cancelled = false;
    private String reason;

    public synchronized boolean isCancelled() {
        return cancelled;
    }

    public synchronized void checkForCancel() {
        if (isCancelled()) {
            onCancel(reason, null);
        }
    }

    /**
     * 本方法被 checkForCancel 调用.默认实现会抛出ExecutionCancelledException
     * @param reason
     * @param suppressedException any error that was encountered during the execution before the operation was cancelled
     */
    protected void onCancel(String reason, Exception suppressedException) {
        RuntimeException e = new ExecutionCancelledException("operation was cancelled reason [" + reason + "]");
        if (suppressedException != null) {
            e.addSuppressed(suppressedException);
        }
        throw e;
    }

    private synchronized boolean add() {
        checkForCancel();
        threads.add(Thread.currentThread());
        return Thread.interrupted();
    }

    public static class ExecutionCancelledException extends RuntimeException {
        public ExecutionCancelledException(String msg) {
            super(msg);
        }
    }

    /**
     *
     * @param interruptable
     */
    public void execute(Interruptable interruptable) {
        try {
            executeIO(interruptable);
        } catch (IOException e) {
            //将已检查异常转换成运行时异常
            throw new RuntimeException("unexpected IO exception", e);
        }
    }

    public void executeIO(IOInterruptable interruptable)throws IOException {
        boolean wasInterrupted = add();
        boolean cancelledByExternalInterrupt = false;
        RuntimeException runtimeException = null;
        IOException ioException = null;

        try {
            interruptable.run();
        } catch (InterruptedException e) {
            assert cancelled : "interruption via Thread#interrupt() is unsupported. Use CancellableThreads#cancel instead";
            cancelledByExternalInterrupt = !cancelled;
        } catch (RuntimeException e) {
            runtimeException= e;
        } catch (IOException e) {
            ioException = e;
        }finally {
            remove();
        }

        if (wasInterrupted) {
            Thread.currentThread().interrupt();
        }else {
            Thread.interrupted();
        }

        synchronized (this) {
            if (isCancelled()) {
                onCancel(reason, ioException != null ? ioException : runtimeException);
            } else if (ioException != null) {
                //if we are not canceling,we throw the original exception
                throw ioException;
            }
            if (runtimeException != null) {
                //if we are not canceling,we throw the original exception
                throw runtimeException;
            }

        }
        if (cancelledByExternalInterrupt) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interruption via Thread#interrupt() is unsupported Use CancellableThreads#cancel instead");
        }

    }

    private synchronized void remove() {
        threads.remove(Thread.currentThread());
    }


    public interface Interruptable extends IOInterruptable{
        @Override
        void run() throws InterruptedException;
    }

    public interface IOInterruptable {
        void run() throws IOException, InterruptedException;
    }
}
