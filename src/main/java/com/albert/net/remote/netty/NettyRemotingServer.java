package com.albert.net.remote.netty;

import com.albert.net.remote.RPCHook;
import com.albert.net.remote.RemotingServer;

import java.util.List;

public class NettyRemotingServer extends AbstractNettyRemoting
    implements RemotingServer {
    public NettyRemotingServer(int oneWayPermits, int asyncPermits) {
        super(oneWayPermits, asyncPermits);
    }

    public void start() {

    }

    public void shutdown() {

    }

    public void registerRemoteHook(RPCHook remoteHook) {

    }

    @Override
    public void registerRPCHook(RPCHook hooks) {

    }

    @Override
    public List<RPCHook> rpcHookList() {
        return null;
    }
}
