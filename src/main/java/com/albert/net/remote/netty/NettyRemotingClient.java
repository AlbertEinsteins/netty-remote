package com.albert.net.remote.netty;

import cn.hutool.core.lang.Pair;
import com.albert.net.remote.InvokeCallback;
import com.albert.net.remote.RPCHook;
import com.albert.net.remote.RemotingClient;
import com.albert.net.remote.common.RemotingUtils;
import com.albert.net.remote.exception.RemotingConnectException;
import com.albert.net.remote.exception.RemotingTimeoutException;
import com.albert.net.remote.exception.RemotingSendRequestException;
import com.albert.net.remote.protocol.RemotingCommand;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class NettyRemotingClient extends AbstractNettyRemoting
        implements RemotingClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(NettyRemotingClient.class);

    private static final long LOCK_TIMEOUT_MILLIS = 3000;

    private final NettyClientConfig nettyClientConfig;

    private final Bootstrap bootstrap = new Bootstrap();

    private final EventLoopGroup eventLoopGroupWorker;

    private final Lock lockChannelTable = new ReentrantLock();

    /**
     * addr: string; <ip:port>
     */
    private final ConcurrentHashMap<String /* addr */, ChannelWrapper> channelTable = new ConcurrentHashMap<>();

    private final Timer timer = new Timer("CleanResponseTableTimer", true);

    private final ExecutorService publicExecutorService;


    /* 事件处理监听器 */
    private final NettyEventListener nettyEventListener;

    private DefaultEventLoopGroup defaultEventLoopGroup;

    private ExecutorService callbackExecutor;

    public NettyRemotingClient(final NettyClientConfig clientConfig) {
        this(clientConfig, null);
    }
    public NettyRemotingClient(final NettyClientConfig clientConfig, final NettyEventListener eventListener) {
        super(clientConfig.getClientOnewaySemaphoreValue(), clientConfig.getClientAsyncSemaphoreValue());
        this.nettyClientConfig = clientConfig;
        this.nettyEventListener = eventListener;

        int publicThreadNums = clientConfig.getClientCallbackExecutorThreads();
        if(publicThreadNums <= 0) {
            publicThreadNums = 4;
        }

        this.publicExecutorService = Executors.newFixedThreadPool(publicThreadNums, new ThreadFactory() {
            private final AtomicInteger threadIdx = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r,"NettyClientPublicExecutorService_" + threadIdx.getAndIncrement());
            }
        });

        this.eventLoopGroupWorker = new NioEventLoopGroup(1, new ThreadFactory() {
            private final AtomicInteger threadIdx = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "NettyEventLoopGroupWorker_" + threadIdx.getAndIncrement());
            }
        });
    }


    public void start() {
        this.defaultEventLoopGroup = new DefaultEventLoopGroup(nettyClientConfig.getClientWorkerThreads(), new ThreadFactory() {
            private final AtomicInteger threadIdx = new AtomicInteger(0);

            public Thread newThread(Runnable r) {
                return new Thread(r, "DefaultEventLoopGroup" + threadIdx.getAndIncrement());
            }
        });
        this.bootstrap.group(eventLoopGroupWorker).channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, false)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(defaultEventLoopGroup,
                                new NettyEncoder(),
                                new NettyDecoder(),
                                new NettyClientHandler());
                    }
                });

        this.timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                NettyRemotingClient.this.scanResponseTable();
            }
        }, 3 * 1000, 1000);

    }

    public void shutdown() {
        try {
            this.timer.cancel();
            for (ChannelWrapper wrapper : this.channelTable.values()) {
                closeChannel(null, wrapper.getChannel());
            }
            this.channelTable.clear();

            this.eventLoopGroupWorker.shutdownGracefully();
            if(this.defaultEventLoopGroup != null) {
                this.defaultEventLoopGroup.shutdownGracefully();
            }
        } catch (Exception e) {
            LOGGER.error("NettyClient shutdown exception", e);
        }


        if(this.publicExecutorService != null) {
            this.publicExecutorService.shutdown();
        }
    }

    public void closeChannel(final String addr, final Channel channel) {
        if(channel == null) {
            return ;
        }
        String addrRemote = addr;
        try {
            if(lockChannelTable.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                boolean isRemove = true;

                ChannelWrapper wrapper = channelTable.get(addr);
                if(wrapper == null) {
                    isRemove = false;
                    LOGGER.info("closeChannel: the channel[{}] has been removed from the channel table before", addrRemote);
                } else if(wrapper.getChannel() != channel) {
                    isRemove = false;
                    LOGGER.info("closeChannel: the channel[{}] has been closed from the channel table before", addrRemote);
                }
                if(isRemove) {
                    channelTable.remove(addr);
                    channel.close();
                }
            } else {
                LOGGER.warn("method closeChannel tryLock channelTable, failed: {} ms", LOCK_TIMEOUT_MILLIS);
            }
        } catch (InterruptedException e) {
            LOGGER.warn("method closeChannel interrupt exception occurred");
        }
    }


    /**
     * create channel according to addr
     * @param addr ip:port
     * @return Channel
     */
    private Channel createChannel(final String addr) {
        ChannelWrapper cw = this.channelTable.get(addr);
        if(cw != null && cw.isActive()) {
            return cw.getChannel();
        }

        try {
            if(this.lockChannelTable.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                boolean isCreateNew = true;

                cw = this.channelTable.get(addr);
                if(cw != null) {
                    if(cw.isActive()) {
                        return cw.getChannel();
                    } else if(cw.getChannelFuture().isDone()) {
                        isCreateNew = false;
                    }
                }

                if(isCreateNew) {
                    ChannelFuture cf = this.bootstrap.connect(RemotingUtils.addrToNetAddress(addr));
                    cw = new ChannelWrapper(cf);
                    this.channelTable.put(addr, cw);
                }
            } else {
                LOGGER.warn("createChannel: try to lock channel table, but timeout, {}ms", LOCK_TIMEOUT_MILLIS);
            }
        } catch (Exception e) {
            LOGGER.error("createChannel exception occurred", e);
        }

        if(cw != null) {
            ChannelFuture cf = cw.getChannelFuture();
            if(cf.awaitUninterruptibly(3000, TimeUnit.MILLISECONDS)) {
                if(cf.isDone()) {
                    return cf.channel();
                } else {
                    LOGGER.warn("createChannel create failed, " + cf.cause());
                }
            } else {
                LOGGER.warn("createChannel connect timeout");
            }
        }
        return null;
    }

    private Channel getOrCreateChannel(final String addr) {
        ChannelWrapper cw = this.channelTable.get(addr);
        if(cw != null && cw.isActive()) {
            return cw.getChannel();
        }
        return this.createChannel(addr);
    }

    @Override
    public RemotingCommand invokeSync(String addr, RemotingCommand request, long timeoutMillis)
            throws RemotingConnectException, RemotingTimeoutException, RemotingSendRequestException, InterruptedException {
        long beginTimeStamp = System.currentTimeMillis();
        final Channel channel = this.getOrCreateChannel(addr);

        if(channel != null && channel.isActive()) {
            try {
                doBeforeHooks(addr, request);
                long costTime = System.currentTimeMillis() - beginTimeStamp;
                if(costTime > timeoutMillis) {
                    throw new RemotingTimeoutException("invokeSync timeout " + timeoutMillis);
                }
                RemotingCommand response = this.invokeSyncImpl(channel, request, timeoutMillis);
                doAfterHooks(addr, request, response);
                return response;
            } catch (RemotingSendRequestException e) {
                LOGGER.warn("invokeSync send request failed, close the channel {}", addr);
                closeChannel(addr, channel);
                throw e;
            } catch (RemotingTimeoutException e) {
                LOGGER.warn("invokeSync read timeout exception, close the channel {}", addr);
                closeChannel(addr, channel);
                throw e;
            }
        } else {
            LOGGER.warn("invokeSync send request failed, close the channel {}", addr);
            throw new RemotingConnectException("invokeSync channel connected failed exception channel, " + addr);
        }
    }

    @Override
    public void invokeAsync(String addr, RemotingCommand request, long timeoutMillis, InvokeCallback callback) throws RemotingConnectException, RemotingTimeoutException, InterruptedException, RemotingSendRequestException {
        long beginTimeStamp = System.currentTimeMillis();
        final Channel channel = this.getOrCreateChannel(addr);
        if(channel != null && channel.isActive()) {
            try {
                doBeforeHooks(addr, request);
                long costTime = System.currentTimeMillis() - beginTimeStamp;
                if(costTime > timeoutMillis) {
                    throw new RemotingTimeoutException("invokeAsync timeout " + timeoutMillis);
                }
                this.invokeAsyncImpl(channel, request, timeoutMillis - costTime, callback);
            } catch (RemotingTimeoutException e) {
                LOGGER.warn("invokeAsync remoting timeout exception, close the channel {}", addr);
                closeChannel(addr, channel);
                throw e;
            } catch (RemotingSendRequestException e) {
                LOGGER.warn("invokeAsync send request failed, close the channel {}", addr);
                closeChannel(addr, channel);
                throw e;
            }
        } else {
            LOGGER.warn("invokeAsync connect failed, close the channel {}", addr);
            throw new RemotingConnectException("invokeAsync channel connected failed exception channel, " + addr);
        }
    }

    @Override
    public void invokeOneway(String addr, RemotingCommand request, long timeoutMillis) throws InterruptedException, RemotingTimeoutException, RemotingSendRequestException, RemotingConnectException {
        long beginTimeStamp = System.currentTimeMillis();
        final Channel channel = this.getOrCreateChannel(addr);
        if(channel != null && channel.isActive()) {
            try {
                doBeforeHooks(addr, request);
                long costTime = System.currentTimeMillis() - beginTimeStamp;
                if(costTime > timeoutMillis) {
                    throw new RemotingTimeoutException("invokeAsync timeout " + timeoutMillis);
                }
                this.invokeOnewayImpl(channel, request, timeoutMillis - costTime);
            } catch (RemotingTimeoutException e) {
                LOGGER.warn("invokeAsync remoting timeout exception, close the channel {}", addr);
                closeChannel(addr, channel);
                throw e;
            } catch (RemotingSendRequestException e) {
                LOGGER.warn("invokeAsync send request failed, close the channel {}", addr);
                closeChannel(addr, channel);
                throw e;
            }
        } else {
            LOGGER.warn("invokeAsync connect failed, close the channel {}", addr);
            closeChannel(addr, channel);
            throw new RemotingConnectException("invokeAsync channel connected failed exception channel, " + addr);
        }
    }

    @Override
    public void registerProcessor(int code, RemotingProcessor processor, ExecutorService executorService) {
        ExecutorService curExecutor = executorService;
        if(curExecutor == null) {
            curExecutor = this.publicExecutorService;
        }
        Pair<RemotingProcessor, ExecutorService> pair = new Pair<>(processor, curExecutor);
        this.processorTable.put(code, pair);
    }

    @Override
    public void registerRPCHook(RPCHook hook) {
        if(hook != null && !rpcHookList.contains(hook)) {
            this.rpcHookList.add(hook);
        }
    }

    @Override
    public void setCallbackExecutor(ExecutorService executorService) {
        this.callbackExecutor = executorService;
    }

    @Override
    public ExecutorService getCallbackExecutor() {
        return this.callbackExecutor == null ? this.publicExecutorService : this.callbackExecutor;
    }



    static class ChannelWrapper {
        private final ChannelFuture channelFuture;
        public ChannelWrapper(final ChannelFuture channelFuture) {
            this.channelFuture = channelFuture;
        }

        public boolean isWriteable() {
            return channelFuture != null && channelFuture.channel().isWritable();
        }
        public boolean isActive() {
            return channelFuture != null && channelFuture.channel().isActive();
        }

        public ChannelFuture getChannelFuture() {
            return channelFuture;
        }

        public Channel getChannel() {
            return channelFuture.channel();
        }
    }

    class NettyClientHandler extends SimpleChannelInboundHandler<RemotingCommand> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RemotingCommand msg) throws Exception {
            processMessage(ctx, msg);
        }
    }
}
