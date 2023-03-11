package com.albert.net.remote;

import com.albert.net.remote.netty.RemotingProcessor;
import com.albert.net.remote.protocol.RemotingMessage;

import java.util.concurrent.ExecutorService;

public interface RemotingClient extends RemotingService {

    /**
     * 同步执行
     * @param addr
     * @param request
     * @param timeoutMillis
     * @return
     */
    RemotingMessage invokeSync(final String addr, final RemotingMessage request, long timeoutMillis);

    /**
     * 异步执行
     * @param addr
     * @param request
     * @param timeoutMillis
     * @param callback
     */
    void invokeAsync(final String addr, final RemotingMessage request, long timeoutMillis,
                     InvokeCallback callback);

    /**
     * 单向执行
     * @param addr
     * @param request
     * @param timeoutMillis
     * @param callback
     */
    void invokeOneway(final String addr, final RemotingMessage request, long timeoutMillis,
                      InvokeCallback callback);

    void registerProcessor(final int code, final RemotingProcessor processor, ExecutorService executorService);

    ExecutorService getCallbackExecutor();

    void setCallbackExecutor(final ExecutorService executorService);
}
