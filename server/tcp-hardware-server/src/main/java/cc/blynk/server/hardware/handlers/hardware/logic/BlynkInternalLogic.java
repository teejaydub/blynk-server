package cc.blynk.server.hardware.handlers.hardware.logic;

import cc.blynk.server.core.dao.ota.OTAManager;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.device.Device;
import cc.blynk.server.core.model.device.HardwareInfo;
import cc.blynk.server.core.model.widgets.others.rtc.RTC;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.server.core.session.HardwareStateHolder;
import cc.blynk.utils.StringUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.server.core.protocol.enums.Command.BLYNK_INTERNAL;
import static cc.blynk.server.internal.BlynkByteBufUtil.illegalCommand;
import static cc.blynk.server.internal.BlynkByteBufUtil.makeASCIIStringMessage;
import static cc.blynk.server.internal.BlynkByteBufUtil.ok;
import static cc.blynk.utils.StringUtils.BODY_SEPARATOR;

/**
 *
 * Simple handler that accepts info command from hardware.
 * At the moment only 1 param is used "h-beat".
 *
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
@ChannelHandler.Sharable
public class BlynkInternalLogic {

    private static final Logger log = LogManager.getLogger(BlynkInternalLogic.class);

    private final OTAManager otaManager;
    private final int hardwareIdleTimeout;
    private final String noThrottleBoardType;

    public BlynkInternalLogic(OTAManager otaManager, int hardwareIdleTimeout, String noThrottleBoardType) {
        this.otaManager = otaManager;
        this.hardwareIdleTimeout = hardwareIdleTimeout;
        this.noThrottleBoardType = noThrottleBoardType;
    }

    public void messageReceived(ChannelHandlerContext ctx, HardwareStateHolder state, StringMessage message) {
        String[] messageParts = message.body.split(StringUtils.BODY_SEPARATOR_STRING);

        if (messageParts.length == 0 || messageParts[0].length() == 0) {
            ctx.writeAndFlush(illegalCommand(message.id), ctx.voidPromise());
            return;
        }

        final String cmd = messageParts[0];

        switch (cmd.charAt(0)) {
            case 'v' :
                parseHardwareInfo(ctx, messageParts, state, message.id);
                break;
            case 'a' :
                break;
            case 'r' :
                sendRTC(ctx, state, message.id);
                break;
            case 'o' :
                break;
        }

    }

    private void sendRTC(ChannelHandlerContext ctx, HardwareStateHolder state, int msgId) {
        DashBoard dashBoard = state.dash;
        RTC rtc = dashBoard.getWidgetByType(RTC.class);
        if (rtc != null && ctx.channel().isWritable()) {
            ctx.writeAndFlush(makeASCIIStringMessage(BLYNK_INTERNAL, msgId, "rtc" + BODY_SEPARATOR + rtc.getTime()),
                    ctx.voidPromise());
        }
    }

    private void parseHardwareInfo(ChannelHandlerContext ctx, String[] messageParts,
                                   HardwareStateHolder state, int msgId) {
        HardwareInfo hardwareInfo = new HardwareInfo(messageParts);
        int newHardwareInterval = hardwareInfo.heartbeatInterval;

        log.trace("Info command. heartbeat interval {}", newHardwareInterval);

        if (hardwareIdleTimeout != 0 && newHardwareInterval > 0) {
            int newReadTimeout = (int) Math.ceil(newHardwareInterval * 2.3D);
            log.debug("Changing read timeout interval to {}", newReadTimeout);
            ctx.pipeline().replace(ReadTimeoutHandler.class,
                    "H_ReadTimeout_Replaced", new ReadTimeoutHandler(newReadTimeout));
        }

        DashBoard dashBoard = state.dash;
        Device device = state.device;

        if (device != null) {
            otaManager.initiateHardwareUpdate(ctx, state.userKey, hardwareInfo, dashBoard, device);
            device.hardwareInfo = hardwareInfo;
            dashBoard.updatedAt = System.currentTimeMillis();

            if (!this.noThrottleBoardType.isEmpty()
                && hardwareInfo.boardType.equals(this.noThrottleBoardType)) {
                try {
                    log.trace("Removing throttle for boardType '{}'.",
                        hardwareInfo.boardType, this.noThrottleBoardType);
                    ctx.pipeline().remove(ChannelTrafficShapingHandler.class);
                } catch (Exception e) {
                    log.debug("Couldn't remove channel throttle.");
                }
            }
        }

        ctx.writeAndFlush(ok(msgId), ctx.voidPromise());
    }

}
