package cc.blynk.server.hardware;

import cc.blynk.server.Holder;
import cc.blynk.server.core.BaseServer;
import cc.blynk.server.core.protocol.handlers.decoders.MessageDecoder;
import cc.blynk.server.core.protocol.handlers.encoders.MessageEncoder;
import cc.blynk.server.handlers.common.AlreadyLoggedHandler;
import cc.blynk.server.handlers.common.HardwareNotLoggedHandler;
import cc.blynk.server.hardware.handlers.hardware.HardwareChannelStateHandler;
import cc.blynk.server.hardware.handlers.hardware.auth.HardwareLoginHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 */
public class HardwareSSLServer extends BaseServer {

    private final ChannelInitializer<SocketChannel> channelInitializer;

    private static final int WRITE_LIMIT = 20;  // bytes per check period
    private static final int MS_PER_CHECK = 100;  // milliseconds per check period

    public HardwareSSLServer(Holder holder) {
        super(holder.props.getProperty("listen.address"),
                holder.props.getIntProperty("hardware.ssl.port"), holder.transportTypeHolder);

        HardwareLoginHandler hardwareLoginHandler = new HardwareLoginHandler(holder, port);
        HardwareChannelStateHandler hardwareChannelStateHandler =
                new HardwareChannelStateHandler(holder.sessionDao, holder.gcmWrapper);
        AlreadyLoggedHandler alreadyLoggedHandler = new AlreadyLoggedHandler();

        final int hardTimeoutSecs = holder.limits.hardwareIdleTimeout;

        this.channelInitializer = new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                final ChannelPipeline pipeline = ch.pipeline();
                if (hardTimeoutSecs > 0) {
                    pipeline.addLast("HSSLReadTimeout", new ReadTimeoutHandler(hardTimeoutSecs));
                }
                pipeline.addLast("HSSL", holder.sslContextHolder.sslCtx.newHandler(ch.alloc()))
                .addLast("HSSLChannelState", hardwareChannelStateHandler)
                .addLast("HSSLMessageDecoder", new MessageDecoder(holder.stats))
                .addLast("HSSLMessageEncoder", new MessageEncoder(holder.stats))
                .addLast("HSSLChannelShaper", new ChannelTrafficShapingHandler(WRITE_LIMIT, 0, MS_PER_CHECK))
                .addLast("HSSLLogin", hardwareLoginHandler)
                .addLast("HSSLNotLogged", new HardwareNotLoggedHandler())
                .addLast("HSSLAlreadyLogged", alreadyLoggedHandler);
            }
        };
    }

    @Override
    public ChannelInitializer<SocketChannel> getChannelInitializer() {
        return channelInitializer;
    }

    @Override
    protected String getServerName() {
        return "Hardware SSL";
    }

    @Override
    public void close() {
        System.out.println("Shutting down Hardware SSL server...");
        super.close();
    }

}
