package com.netty.remote;

import com.albert.net.remote.netty.NettyRemotingServer;
import com.albert.net.remote.netty.NettyServerConfig;
import com.albert.net.remote.netty.RemotingProcessor;
import com.albert.net.remote.protocol.RemotingMessage;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.StandardCharsets;

public class Server {
    static class MyProcessor implements RemotingProcessor {
        @Override
        public RemotingMessage process(ChannelHandlerContext ctx, RemotingMessage request) {
            String receive = new String(request.getBody(), StandardCharsets.UTF_8);
            System.out.println(receive);

            RemotingMessage response = RemotingMessage.createResponse(request.getCode(), "success");
            response.setBody(request.getBody());
            return response;
        }

        @Override
        public boolean rejectRequest() {
            return false;
        }
    }
    public static void main(String[] args) {
        NettyServerConfig serverConfig = new NettyServerConfig();
        serverConfig.setListenPort(7800);

        NettyRemotingServer server = new NettyRemotingServer(serverConfig);
        server.registerProcessor(100, new MyProcessor(), null);

        server.start();
    }
}
