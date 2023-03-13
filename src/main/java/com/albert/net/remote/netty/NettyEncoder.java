package com.albert.net.remote.netty;

import com.albert.net.remote.protocol.RemotingMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ChannelHandler.Sharable
public class NettyEncoder extends MessageToByteEncoder<RemotingMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NettyEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, RemotingMessage msg, ByteBuf out) throws Exception {
        try {
            byte[] bytes = RemotingMessage.encode(msg);
            out.writeBytes(bytes);
        } catch (Exception e) {
            LOGGER.error("encode error: ", e);
            ctx.close();
        }
    }
}
