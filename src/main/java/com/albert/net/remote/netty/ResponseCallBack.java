package com.albert.net.remote.netty;

import com.albert.net.remote.protocol.RemotingMessage;

public interface ResponseCallBack {
    void operationComplete(RemotingMessage response);
}
