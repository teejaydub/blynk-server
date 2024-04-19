package cc.blynk.server.hardware.handlers.hardware;

import cc.blynk.server.Holder;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.server.core.session.HardwareStateHolder;
import cc.blynk.server.core.session.StateHolderBase;
import cc.blynk.server.handlers.BaseSimpleChannelInboundHandler;
import cc.blynk.server.handlers.common.PingLogic;
import cc.blynk.server.hardware.handlers.hardware.logic.BlynkInternalLogic;
import cc.blynk.server.hardware.handlers.hardware.logic.BridgeLogic;
import cc.blynk.server.hardware.handlers.hardware.logic.HardwareLogic;
import cc.blynk.server.hardware.handlers.hardware.logic.HardwareSyncLogic;
import cc.blynk.server.hardware.handlers.hardware.logic.MailLogic;
import cc.blynk.server.hardware.handlers.hardware.logic.PushLogic;
import cc.blynk.server.hardware.handlers.hardware.logic.SetWidgetPropertyLogic;
import cc.blynk.server.hardware.handlers.hardware.logic.SmsLogic;
import cc.blynk.server.hardware.handlers.hardware.logic.TwitLogic;
import io.netty.channel.ChannelHandlerContext;

import static cc.blynk.server.core.protocol.enums.Command.BLYNK_INTERNAL;
import static cc.blynk.server.core.protocol.enums.Command.BRIDGE;
import static cc.blynk.server.core.protocol.enums.Command.EMAIL;
import static cc.blynk.server.core.protocol.enums.Command.HARDWARE;
import static cc.blynk.server.core.protocol.enums.Command.HARDWARE_SYNC;
import static cc.blynk.server.core.protocol.enums.Command.PING;
import static cc.blynk.server.core.protocol.enums.Command.PUSH_NOTIFICATION;
import static cc.blynk.server.core.protocol.enums.Command.SET_WIDGET_PROPERTY;
import static cc.blynk.server.core.protocol.enums.Command.SMS;
import static cc.blynk.server.core.protocol.enums.Command.TWEET;


/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 29.07.15.
 */
public class HardwareHandler extends BaseSimpleChannelInboundHandler<StringMessage> {

    public final HardwareStateHolder state;
    private final HardwareLogic hardware;
    private final MailLogic email;
    private final BridgeLogic bridge;
    private final PushLogic push;
    private final TwitLogic tweet;
    private final SmsLogic smsLogic;
    private final SetWidgetPropertyLogic propertyLogic;
    private final BlynkInternalLogic info;

    public HardwareHandler(Holder holder, HardwareStateHolder stateHolder) {
        super(StringMessage.class, holder.limits);
        this.hardware = new HardwareLogic(holder, stateHolder.user.email);
        this.bridge = new BridgeLogic(holder.sessionDao, hardware);

        this.email = new MailLogic(holder.blockingIOProcessor,
                holder.mailWrapper, holder.limits.notificationPeriodLimitSec);
        this.push = new PushLogic(holder.gcmWrapper, holder.limits.notificationPeriodLimitSec);
        this.tweet = new TwitLogic(holder.blockingIOProcessor,
                holder.twitterWrapper, holder.limits.notificationPeriodLimitSec);
        this.smsLogic = new SmsLogic(holder.smsWrapper, holder.limits.notificationPeriodLimitSec);
        this.propertyLogic = new SetWidgetPropertyLogic(holder.sessionDao);
        this.info = new BlynkInternalLogic(holder.otaManager, holder.limits.hardwareIdleTimeout,
                holder.props.getProperty("net.throttleBoardType", ""));

        this.state = stateHolder;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, StringMessage msg) {
        switch (msg.command) {
            case HARDWARE:
                hardware.messageReceived(ctx, state, msg);
                break;
            case PING:
                PingLogic.messageReceived(ctx, msg.id);
                break;
            case BRIDGE:
                bridge.messageReceived(ctx, state, msg);
                break;
            case EMAIL:
                email.messageReceived(ctx, state, msg);
                break;
            case PUSH_NOTIFICATION:
                push.messageReceived(ctx, state, msg);
                break;
            case TWEET:
                tweet.messageReceived(ctx, state, msg);
                break;
            case SMS:
                smsLogic.messageReceived(ctx, state, msg);
                break;
            case HARDWARE_SYNC:
                HardwareSyncLogic.messageReceived(ctx, state, msg);
                break;
            case BLYNK_INTERNAL:
                info.messageReceived(ctx, state, msg);
                break;
            case SET_WIDGET_PROPERTY:
                propertyLogic.messageReceived(ctx, state, msg);
                break;
        }
    }

    @Override
    public StateHolderBase getState() {
        return state;
    }
}
