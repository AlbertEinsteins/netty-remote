package com.netty.remote;


import com.albert.net.remote.InvokeCallback;
import com.albert.net.remote.exception.RemotingConnectException;
import com.albert.net.remote.exception.RemotingSendRequestException;
import com.albert.net.remote.exception.RemotingTimeoutException;
import com.albert.net.remote.netty.*;
import com.albert.net.remote.protocol.RemotingMessage;

import java.nio.charset.StandardCharsets;

public class Client {
    public static void main(String[] args) throws RemotingConnectException, RemotingSendRequestException, RemotingTimeoutException, InterruptedException {
        NettyClientConfig clientConfig = new NettyClientConfig();
        NettyRemotingClient client = new NettyRemotingClient(clientConfig);


        client.start();

        RemotingMessage msg = new RemotingMessage();
        String body = "123";
        msg.setBody(body.getBytes(StandardCharsets.UTF_8));

        // set request code
        msg.setCode(100);

//        final InvokeCallback callback = new InvokeCallback() {
//            @Override
//            public void operationComplete(ResponseFuture responseFuture) {
//                RemotingMessage message = null;
//                try {
//                    message = responseFuture.waitResponse();
//                } catch (InterruptedException e) {
//                    System.out.println(e);
//                    throw new RuntimeException(e);
//                }
//                System.out.println(message);
//            }
//        };

        client.invokeOneway("127.0.0.1:7800", msg, 10000);
    }
}
