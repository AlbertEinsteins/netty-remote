package com.albert.net.remote.netty;

import com.albert.net.remote.protocol.RemotingCommand;
import io.netty.channel.ChannelHandlerContext;

public interface RemotingProcessor {
    RemotingCommand process(ChannelHandlerContext ctx, RemotingCommand request);

    boolean rejectRequest();
}
