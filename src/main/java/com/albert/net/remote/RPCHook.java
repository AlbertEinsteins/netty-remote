package com.albert.net.remote;

import com.albert.net.remote.protocol.RemotingMessage;

public interface RPCHook {
    /**
     * 发送前执行前执行的操作
     */
    void doBefore(final String addr, final RemotingMessage request);
    /**
     * 发送前执行后执行的操作
     */
    void doAfter(final String addr, final RemotingMessage request, final RemotingMessage response);
}
