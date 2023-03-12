package com.albert.net.remote.common;


import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class RemotingUtils {
    private RemotingUtils() { }

    public static String parseRemoteAddress(final Channel channel) {
        String remoteAddress = channel.remoteAddress().toString();
        return remoteAddress;
    }

    public static InetSocketAddress addrToNetAddress(final String addr) {
        String ip = "";
        int port = 0;
        return InetSocketAddress.createUnresolved(ip, port);
    }
}
