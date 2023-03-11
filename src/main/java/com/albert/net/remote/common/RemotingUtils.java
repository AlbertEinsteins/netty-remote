package com.albert.net.remote.common;


import io.netty.channel.Channel;

public class RemotingUtils {
    private RemotingUtils() { }

    public static String parseRemoteAddress(final Channel channel) {
        String remoteAddress = channel.remoteAddress().toString();
        return remoteAddress;
    }
}
