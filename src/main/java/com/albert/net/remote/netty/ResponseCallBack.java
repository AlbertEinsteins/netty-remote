package com.albert.net.remote.netty;

import com.albert.net.remote.protocol.RemotingCommand;

public interface ResponseCallBack {
    void operationComplete(RemotingCommand response);
}
