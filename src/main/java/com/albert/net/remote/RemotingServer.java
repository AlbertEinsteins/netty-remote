package com.albert.net.remote;

import com.albert.net.remote.exception.RemotingSendRequestException;
import com.albert.net.remote.exception.RemotingTimeoutException;
import com.albert.net.remote.netty.RemotingProcessor;
import com.albert.net.remote.protocol.RemotingCommand;
import io.netty.channel.Channel;

import java.util.concurrent.ExecutorService;

public interface RemotingServer extends RemotingService {
    void registerProcessor(final int requestCode, final RemotingProcessor processor,
                           final ExecutorService executor);
    int localListenPort();

    void registerDefaultProcessor(final RemotingProcessor processor, final ExecutorService executor);

    RemotingCommand invokeSync(final Channel channel, final RemotingCommand request,
                               final long timeoutMillis) throws InterruptedException, RemotingSendRequestException,
            RemotingTimeoutException;

    void invokeAsync(final Channel channel, final RemotingCommand request, final long timeoutMillis,
                     final InvokeCallback invokeCallback) throws InterruptedException,
            RemotingTimeoutException, RemotingSendRequestException;

    void invokeOneway(final Channel channel, final RemotingCommand request, final long timeoutMillis)
            throws InterruptedException, RemotingTimeoutException, RemotingSendRequestException;
}
