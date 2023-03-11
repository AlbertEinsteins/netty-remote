package com.albert.net.remote;

public interface RemotingService {
    /**
     * 服务启动
     */
    void start();

    /**
     * 关闭服务
     */
    void shutdown();

    /**
     * 注册钩子函数
     * 在消息发送前后起作用
     * @param hooks 钩子
     */
    void registerRPCHook(final RPCHook hooks);
}
