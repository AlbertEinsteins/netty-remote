package com.albert.net.remote.netty;

import com.albert.net.remote.InvokeCallback;
import com.albert.net.remote.protocol.RemotingMessage;
import io.netty.channel.Channel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ResponseFuture {
    private final int requestId;

    private final Channel processChannel;
    private final long timeoutMillis;
    private final InvokeCallback invokeCallback;

    private final AtomicBoolean invokeOnce = new AtomicBoolean(true);
    private final long beginTimestamp = System.currentTimeMillis();

    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    private final SemaphoreReleaseOnlyOnce once;

    private volatile RemotingMessage response;

    private Throwable cause;

    private boolean isSendSuccess = true;

    public ResponseFuture(int requestId, Channel processChannel, long timeoutMillis,
                          InvokeCallback invokeCallback, SemaphoreReleaseOnlyOnce once) {
        this.requestId = requestId;
        this.processChannel = processChannel;
        this.timeoutMillis = timeoutMillis;
        this.invokeCallback = invokeCallback;
        this.once = once;
    }

    public boolean isTimeout() {
        return this.beginTimestamp + timeoutMillis < System.currentTimeMillis();
    }



    public RemotingMessage waitResponse() throws InterruptedException {
        this.countDownLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        return this.response;
    }

    public void putResponse(RemotingMessage response) {
        this.response = response;
        this.countDownLatch.countDown();
    }

    public void executeCallback() {
        if(invokeCallback != null) {
            if(invokeOnce.compareAndSet(true, false)) {
                invokeCallback.operationComplete(this);
            }
        }
    }
    public void release() {
        if(once != null) {
            once.release();
        }
    }

    public void setResponse(RemotingMessage response) {
        this.response = response;
    }

    public int getRequestId() {
        return requestId;
    }

    public Channel getProcessChannel() {
        return processChannel;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public InvokeCallback getInvokeCallback() {
        return invokeCallback;
    }

    public long getBeginTimestamp() {
        return beginTimestamp;
    }

    public Throwable getCause() {
        return cause;
    }

    public void setCause(Throwable cause) {
        this.cause = cause;
    }

    public boolean isSendSuccess() {
        return isSendSuccess;
    }

    public void setSendSuccess(boolean sendSuccess) {
        isSendSuccess = sendSuccess;
    }
}