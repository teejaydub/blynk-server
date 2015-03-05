package cc.blynk.server.handlers.workflow;

import cc.blynk.common.model.messages.protocol.PingMessage;
import cc.blynk.common.utils.ServerProperties;
import cc.blynk.server.dao.FileManager;
import cc.blynk.server.dao.SessionsHolder;
import cc.blynk.server.dao.UserRegistry;
import cc.blynk.server.model.auth.Session;
import cc.blynk.server.model.auth.User;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

import static cc.blynk.common.enums.Response.OK;
import static cc.blynk.common.model.messages.MessageFactory.produce;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
@ChannelHandler.Sharable
public class PingHandler extends BaseSimpleChannelInboundHandler<PingMessage> {

    public PingHandler(ServerProperties props, FileManager fileManager, UserRegistry userRegistry, SessionsHolder sessionsHolder) {
        super(props, fileManager, userRegistry, sessionsHolder);
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, User user, PingMessage message) throws Exception {
        Session group = sessionsHolder.getUserSession().get(user);
        List<ChannelFuture> futures = group.sendMessageToHardware(message);

        int length = futures.size();
        //todo works for now only for 1 hardware, not for many
        for (ChannelFuture future : futures) {
            future.addListener(future1 -> {
                ctx.channel().writeAndFlush(produce(message.id, OK));
            });
        }
    }



}
