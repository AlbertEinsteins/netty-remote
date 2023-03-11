package org.example;

import com.albert.net.remote.netty.NettyDecoder;
import com.albert.net.remote.netty.NettyEncoder;
import com.albert.net.remote.protocol.RemotingMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

public class CodecTester {

    @Test
    public void testCodec() {
        ChannelInitializer<EmbeddedChannel> i = new ChannelInitializer<EmbeddedChannel>() {
            @Override
            protected void initChannel(EmbeddedChannel ch) throws Exception {
                ch.pipeline().addLast(
                        new NettyEncoder(),
                        new NettyDecoder());
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(i);

        for(int c = 0; c < 3; c++) {
            RemotingMessage request = RemotingMessage.createRequest(0);
            request.setInfo("test info");
            channel.writeAndFlush(request);
            //打印出站数据
            ByteBuf out = channel.readOutbound();

            System.out.println(out.readableBytes());
            channel.writeInbound(out);

            RemotingMessage in = channel.readInbound();
            System.out.println(in);
        }


    }
}
