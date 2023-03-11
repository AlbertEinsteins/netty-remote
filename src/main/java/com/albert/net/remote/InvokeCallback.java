package com.albert.net.remote;

import com.albert.net.remote.netty.ResponseFuture;

public interface InvokeCallback {
    void operationComplete(ResponseFuture responseFuture);
}
