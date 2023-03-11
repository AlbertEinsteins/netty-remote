package com.albert.net.remote.netty;

import com.albert.net.remote.protocol.RemotingMessage;
import io.netty.channel.ChannelHandlerContext;

public interface RemotingProcessor {
    RemotingMessage process(ChannelHandlerContext ctx, RemotingMessage request);

    boolean rejectRequest();
}
