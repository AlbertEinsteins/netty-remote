package com.albert.net.remote.netty;

import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.ObjectUtil;
import com.albert.net.remote.InvokeCallback;
import com.albert.net.remote.RPCHook;
import com.albert.net.remote.common.RemotingUtils;
import com.albert.net.remote.exception.RemotingTimeoutException;
import com.albert.net.remote.exception.RemotingSendRequestException;
import com.albert.net.remote.protocol.NettyResponseCode;
import com.albert.net.remote.protocol.RemotingCommand;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

public abstract class AbstractNettyRemoting {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractNettyRemoting.class);

    /**
     * 对应状态码处理器表
     */
    protected final Map<Integer /* code */, Pair<RemotingProcessor, ExecutorService>> processorTable
            = new HashMap<>(64);

    protected Pair<RemotingProcessor, ExecutorService> defaultProcessor;
    /**
     * 缓存请求-响应的表
     */
    protected final ConcurrentHashMap<Integer /* reqId */, ResponseFuture> responseTable
            = new ConcurrentHashMap<>(256);
    /* 单向最大请求数 */
    protected Semaphore semaphoreOneWay;
    /* 异步最大请求数 */
    protected Semaphore semaphoreAsync;

    protected  List<RPCHook> rpcHookList = new ArrayList<>();

    // 回调函数执行线程
    public abstract ExecutorService getCallbackExecutor() ;



    public AbstractNettyRemoting(int oneWayPermits, int asyncPermits) {
        this.semaphoreOneWay = new Semaphore(oneWayPermits, true);
        this.semaphoreAsync = new Semaphore(asyncPermits, true);
    }

    protected void doBeforeHooks(String addr, RemotingCommand request) {
        List<RPCHook> hooks = rpcHookList;
        if(hooks.size() > 0) {
            for(RPCHook hook : hooks) {
                hook.doBefore(addr, request);
            }
        }
    }

    protected void doAfterHooks(String addr, RemotingCommand request, RemotingCommand response) {
        List<RPCHook> hooks = rpcHookList;
        if(hooks.size() > 0) {
            for(RPCHook hook : hooks) {
                hook.doAfter(addr, request, response);
            }
        }
    }

    public void processMessage(ChannelHandlerContext ctx, RemotingCommand msg) {
        if(msg != null) {
            switch (msg.getType()) {
                case REQUEST:
                    processRequestMessage(ctx, msg);
                    break;
                case RESPONSE:
                    processReceiveMessage(ctx, msg);
                    break;
                default:
                    break;
            }
        }
    }

    public void processRequestMessage(ChannelHandlerContext ctx, RemotingCommand msg) {
        final Pair<RemotingProcessor, ExecutorService> servicePair = processorTable.get(msg.getCode());
        final Pair<RemotingProcessor, ExecutorService> pair = servicePair == null ? defaultProcessor : servicePair;
        final int reqId = msg.getRequestId();

        if(pair == null) {
            LOGGER.error("request code is not supported, check the code");
            RemotingCommand resp = RemotingCommand.createResponse(NettyResponseCode.SYSTEM_BUSY, "system busy, request is rejected");
            resp.setRequestId(reqId);
            ctx.writeAndFlush(resp);
            return ;
        }

        if(pair.getKey().rejectRequest()) {
            LOGGER.warn("request is rejected");
            RemotingCommand resp = RemotingCommand.createResponse(NettyResponseCode.SYSTEM_BUSY, "system busy, request is rejected");
            resp.setRequestId(reqId);
            ctx.writeAndFlush(resp);
            return ;
        }
        // 异步执行
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    String addr = RemotingUtils.parseRemoteAddress(ctx.channel());
                    doBeforeHooks(addr, msg);
                    //回调函数
                    final ResponseCallBack callBack = new ResponseCallBack() {
                        @Override
                        public void operationComplete(RemotingCommand response) {
                            doAfterHooks(addr, msg, response);
                            if(!msg.isOneWayType()) {
                                response.markResponseType();
                                response.setRequestId(reqId);
                                response.setSerialType(msg.getSerialType());
                                try {
                                    ctx.writeAndFlush(response);
                                } catch (Throwable e) {
                                    LOGGER.error("method processRequestMessage write response error", e);
                                    LOGGER.error(msg.toString());
                                    LOGGER.error(response.toString());
                                }
                            }
                        }
                    };

                    RemotingProcessor processor = pair.getKey();
                    RemotingCommand response = processor.process(ctx, msg);
                    callBack.operationComplete(response);
                } catch (Throwable e) {
                    LOGGER.error("request process error");
                    LOGGER.error(msg.toString());
                }
            }
        };
        //将任务提交到线程池
        try {
            pair.getValue().submit(r);
        } catch (RejectedExecutionException e) {
            if((System.currentTimeMillis() % 10000) == 0) {
                LOGGER.warn("task is so much that beyond thread pool ability");
            }
            if(!msg.isOneWayType()) {
                RemotingCommand resp = RemotingCommand.createResponse(NettyResponseCode.SYSTEM_BUSY, "system busy, request is rejected");
                resp.setRequestId(reqId);
                ctx.writeAndFlush(resp);
            }
        }
    }

    public void processReceiveMessage(final ChannelHandlerContext ctx, final RemotingCommand msg) {
        final int reqId = msg.getRequestId();
        ResponseFuture responseFuture = responseTable.get(reqId);
        if(responseFuture != null) {
            // 区别回调
            responseFuture.setResponse(msg);
            responseTable.remove(reqId);

            if(!ObjectUtil.isEmpty(responseFuture.getInvokeCallback())) {
                executeInvokeCallback(responseFuture);
            } else {
                responseFuture.putResponse(msg);
                responseFuture.release();
            }
        } else {
            LOGGER.warn("response dose not match any request");
            LOGGER.warn(msg.toString());
        }
    }

    private void executeInvokeCallback(final ResponseFuture responseFuture) {
        // 决策是否在当前线程执行
        boolean isRunCurThread = false;
        ExecutorService callbackExecutor = this.getCallbackExecutor();
        if(callbackExecutor != null) {
            try {
                callbackExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            responseFuture.executeCallback();
                        } catch (Exception e) {
                            LOGGER.error("error occured in callback executor pool", e);
                        } finally {
                            responseFuture.release();
                        }
                    }
                });
            } catch (Exception e) {
                LOGGER.error("responseFucture callback execute in callBackExecutor failed, will " +
                        "be invoked in the business thread", e);
                isRunCurThread = true;
            }
        } else {
            isRunCurThread = true;
        }

        if(isRunCurThread) {
            try {
                responseFuture.executeCallback();
            } catch (Exception e) {
                LOGGER.error("response Future invoke in business thread error", e);
            } finally {
                responseFuture.release();
            }
        }
    }



    public RemotingCommand invokeSyncImpl(final Channel channel, final RemotingCommand request,
                                          final long timeoutMillis)
            throws RemotingTimeoutException, RemotingSendRequestException, InterruptedException {
        final int reqId = request.getRequestId();
        try {
            ResponseFuture responseFuture = new ResponseFuture(reqId, channel, timeoutMillis, null, null);
            responseTable.put(reqId, responseFuture);
            channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if(future.isSuccess()) {
                        responseFuture.setSendSuccess(true);
                        return ;
                    } else {
                        responseFuture.setSendSuccess(false);
                    }
                    responseTable.remove(reqId);
                    responseFuture.putResponse(null);
                    responseFuture.setCause(future.cause());
                    LOGGER.warn("send request failed on addr: " + RemotingUtils.parseRemoteAddress(channel));
                }
            });

            RemotingCommand resp = responseFuture.waitResponse();
            if(resp == null) {
                if(responseFuture.isSendSuccess()) {
                    throw new RemotingTimeoutException(RemotingUtils.parseRemoteAddress(channel),
                            timeoutMillis, responseFuture.getCause());
                } else {
                    throw new RemotingSendRequestException(RemotingUtils.parseRemoteAddress(channel),
                            responseFuture.getCause());
                }
            }
            return resp;
        } finally {
            responseTable.remove(reqId);
        }
    }

    public void invokeAsyncImpl(final Channel channel, final RemotingCommand request, final long timeoutMillis, InvokeCallback invokeCallback)
            throws InterruptedException, RemotingTimeoutException, RemotingSendRequestException {
        final long beginTimeStamp = System.currentTimeMillis();
        boolean isAcquire = semaphoreAsync.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS);
        if(isAcquire) {
            final SemaphoreReleaseOnlyOnce once = new SemaphoreReleaseOnlyOnce(semaphoreAsync);
            if(beginTimeStamp + timeoutMillis < System.currentTimeMillis()) {
                once.release();
                throw new RemotingTimeoutException("invokeAsyncImpl timeout exception");
            }
            final ResponseFuture responseFuture = new ResponseFuture(request.getRequestId(), channel, timeoutMillis, invokeCallback, once);
            responseTable.put(request.getRequestId(), responseFuture);
            channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if(future.isSuccess()) {
                        responseFuture.setSendSuccess(true);
                        return ;
                    } else {
                        responseFuture.setSendSuccess(false);
                    }
                    // 快速失败
                    requestFail(request.getRequestId());
                }
            });
        } else {
            if(timeoutMillis <= 0) {
                throw new RemotingSendRequestException("invokeAsyncImpl send too fast exception");
            }
            String info = String.format("invokeAsyncImpl timeout, wait in queue: %d, timeout %d",
                    semaphoreAsync.getQueueLength(), timeoutMillis);
            LOGGER.warn(info);
            throw new RemotingTimeoutException(info);
        }
    }
    /* 该方法没有回应，需要自己主动释放信号量(permits) */
    public void invokeOnewayImpl(final Channel channel, final RemotingCommand request, final long timeoutMillis) throws InterruptedException, RemotingSendRequestException, RemotingTimeoutException {
        final long beginTimeStamp = System.currentTimeMillis();
        boolean isAcquire = semaphoreOneWay.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS);
        request.markOneWayType();
        if(isAcquire) {
            final SemaphoreReleaseOnlyOnce once = new SemaphoreReleaseOnlyOnce(semaphoreOneWay);
            if(beginTimeStamp + timeoutMillis < System.currentTimeMillis()) {
                once.release();
                throw new RemotingTimeoutException("invokeOnewayImpl timeout exception");
            }
            try {
                channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        once.release();
                        if(!future.isSuccess()) {
                            LOGGER.warn("invokeOnewayImpl write message error");
                        }
                    }
                });
            } catch (Exception e) {
                once.release();
                LOGGER.warn("invokeOnewayImpl request error");
                throw new RemotingSendRequestException("invokeOnewayImpl send exception", e);
            }

        } else {
            if(timeoutMillis <= 0) {
                throw new RemotingSendRequestException("invokeOnewayImpl send too fast exception");
            }
            String info = String.format("invokeOnewayImpl timeout, wait in queue: %d, timeout %d",
                    semaphoreAsync.getQueueLength(), timeoutMillis);
            LOGGER.warn(info);
            throw new RemotingTimeoutException(info);
        }
    }

    public void requestFail(final int reqId) {
        ResponseFuture responseFuture = responseTable.get(reqId);
        if(responseFuture != null) {
            responseFuture.setSendSuccess(false);
            responseFuture.putResponse(null);
            try {
                responseFuture.executeCallback();
            } catch (Throwable e) {
                LOGGER.warn("method requestFail executeCallBack error");
            } finally {
                responseFuture.release();
            }
        }
    }

    public void scanResponseTable() {
        List<ResponseFuture> expiredReqs = new ArrayList<>();
        //扫描过期请求
        Iterator<Map.Entry<Integer, ResponseFuture>> iter = this.responseTable.entrySet().iterator();
        while(iter.hasNext()) {
            Map.Entry<Integer, ResponseFuture> entry = iter.next();
            ResponseFuture responseFuture = entry.getValue();

            if(responseFuture.getBeginTimestamp() + responseFuture.getTimeoutMillis() + 1000 <= System.currentTimeMillis()) {
                responseFuture.release();
                expiredReqs.add(responseFuture);
                iter.remove();
                LOGGER.warn("method scanResponseTable: a request is timeout, removed");
            }
        }

        // 执行回调
        if(expiredReqs.size() > 0) {
            for(ResponseFuture rf: expiredReqs) {
                try {
                    rf.executeCallback();
                } catch (Exception e) {
                    LOGGER.warn("method scanResponseTable executeCallback exception");
                }
            }
        }
    }
}
