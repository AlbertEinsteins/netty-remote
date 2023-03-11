package com.albert.net.remote.netty;

import com.albert.net.remote.InvokeCallback;
import com.albert.net.remote.RPCHook;
import com.albert.net.remote.RemotingClient;
import com.albert.net.remote.protocol.RemotingMessage;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class NettyRemotingClient extends AbstractNettyRemoting
        implements RemotingClient {

    public NettyRemotingClient(int oneWayPermits, int asyncPermits) {
        super(oneWayPermits, asyncPermits);
    }

    public void start() {

    }

    public void shutdown() {

    }

    public void registerRemoteHook(RPCHook remoteHook) {

    }

    @Override
    public List<RPCHook> rpcHookList() {
        return null;
    }

    @Override
    public RemotingMessage invokeSync(String addr, RemotingMessage request, long timeoutMillis) {
        return null;
    }

    @Override
    public void invokeAsync(String addr, RemotingMessage request, long timeoutMillis, InvokeCallback callback) {

    }

    @Override
    public void invokeOneway(String addr, RemotingMessage request, long timeoutMillis, InvokeCallback callback) {

    }

    @Override
    public void registerProcessor(int code, RemotingProcessor processor, ExecutorService executorService) {

    }

    @Override
    public void registerRPCHook(RPCHook hooks) {

    }

}
