package com.albert.net.remote.common;

import java.util.concurrent.atomic.AtomicInteger;

public class RemotingMessage {
//    private static final
    /* 请求id生成 */
    private static final AtomicInteger idGenerator = new AtomicInteger(0);
    /* 消息包类型 */
    private static final int REQUEST = 0;
    private static final int RESPONSE = 1;


    private final int requestId = idGenerator.getAndIncrement();
    private int flag;
    private transient byte[] body;
}
