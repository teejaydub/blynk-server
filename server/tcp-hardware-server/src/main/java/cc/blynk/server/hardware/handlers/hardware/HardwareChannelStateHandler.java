package cc.blynk.server.hardware.handlers.hardware;

import cc.blynk.server.Holder;
import cc.blynk.server.core.dao.SessionDao;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.auth.Session;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.device.Device;
import cc.blynk.server.core.model.device.Status;
import cc.blynk.server.core.model.widgets.notifications.Notification;
import cc.blynk.server.core.processors.EventorProcessor;
import cc.blynk.server.core.session.HardwareStateHolder;
import cc.blynk.server.hardware.handlers.hardware.logic.OfflineFlagLogic;
import cc.blynk.server.notifications.push.GCMWrapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.ReadTimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

import static cc.blynk.server.internal.StateHolderUtil.getHardState;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/20/2015.
 *
 * Removes channel from session in case it became inactive (closed from client side).
 */
@ChannelHandler.Sharable
public class HardwareChannelStateHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LogManager.getLogger(HardwareChannelStateHandler.class);

    private final SessionDao sessionDao;
    private final GCMWrapper gcmWrapper;
    private final EventorProcessor eventorProcessor;

    public HardwareChannelStateHandler(Holder holder) {
        this.sessionDao = holder.sessionDao;
        this.gcmWrapper = holder.gcmWrapper;
        this.eventorProcessor = holder.eventorProcessor;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel hardwareChannel = ctx.channel();
        HardwareStateHolder state = getHardState(hardwareChannel);
        if (state != null) {
            Session session = sessionDao.userSession.get(state.userKey);
            if (session != null) {
                session.removeHardChannel(hardwareChannel);
                log.trace("Hardware channel disconnect.");
                sentOfflineMessage(ctx, session, state);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof ReadTimeoutException) {
            log.trace("Hardware timeout disconnect.");
        } else {
            super.exceptionCaught(ctx, cause);
        }
    }

    private void sentOfflineMessage(ChannelHandlerContext ctx, Session session, HardwareStateHolder state) {
        DashBoard dashBoard = state.dash;
        Device device = state.device;

        //this is special case.
        //in case hardware quickly reconnects we do not mark it as disconnected
        //as it is already online after quick disconnect.
        //https://github.com/blynkkk/blynk-server/issues/403
        boolean isHardwareConnected = session.isHardwareConnected(dashBoard.id, device.id);
        if (!isHardwareConnected) {
            log.debug("Disconnected device id {}, ip {}", device.id, ctx.channel().remoteAddress());
            device.disconnected();
        }

        if (!dashBoard.isActive || dashBoard.isNotificationsOff) {
            return;
        }

        Notification notification = dashBoard.getWidgetByType(Notification.class);

        if (notification != null && notification.notifyWhenOffline) {
            sendPushNotification(ctx, dashBoard, session, state.user, notification, dashBoard.id, device);
        } else {
            session.sendOfflineMessageToApps(dashBoard.id, device.id);
        }
    }

    private void sendPushNotification(ChannelHandlerContext ctx, DashBoard dashBoard, Session session,
        User user, Notification notification, int dashId, Device device) {
        String dashName = dashBoard.name == null ? "" : dashBoard.name;
        String deviceName = ((device == null || device.name == null) ? "device" : device.name);
        String message = "Your " + deviceName + " went offline. \"" + dashName + "\" project is disconnected.";
        if (notification.notifyWhenOfflineIgnorePeriod == 0 || device == null) {
            notification.push(gcmWrapper,
                    message,
                    dashId
            );
        } else {
            //delayed notification
            //https://github.com/blynkkk/blynk-server/issues/493
            HardwareChannelStateHandler.log.debug("Scheduling notification for device: {}", device);
            ctx.executor().schedule(new DelayedPush(device, session, user, notification,
                message, dashBoard, eventorProcessor),
                    notification.notifyWhenOfflineIgnorePeriod, TimeUnit.MILLISECONDS);
        }
    }

    private final class DelayedPush implements Runnable {

        private final Device device;
        private final Session session;
        private final User user;
        private final Notification notification;
        private final String message;
        private final DashBoard dashBoard;
        private final EventorProcessor eventorProcessor;

        DelayedPush(Device device, Session session, User user, Notification notification,
            String message, DashBoard dashBoard, EventorProcessor eventorProcessor) {
            this.device = device;
            this.session = session;
            this.user = user;
            this.notification = notification;
            this.message = message;
            this.dashBoard = dashBoard;
            this.eventorProcessor = eventorProcessor;
        }

        @Override
        public void run() {
            final long now = System.currentTimeMillis();
            if (device.status == Status.OFFLINE) {
                if (now - device.disconnectTime >= notification.notifyWhenOfflineIgnorePeriod) {
                    HardwareChannelStateHandler.log.debug("Delayed offline set with device: {}", device);

                    notification.push(gcmWrapper,
                            message,
                            dashBoard.id
                    );

                    OfflineFlagLogic.setOffline(notification, dashBoard,
                        device, user, session, eventorProcessor, true);
                } else {
                    HardwareChannelStateHandler.log.debug("Device {} only offline for {}", device,
                        now - device.disconnectTime);
                }
            } else {
                HardwareChannelStateHandler.log.debug("Device {} no longer offline.", device);
            }
        }
    }

}
