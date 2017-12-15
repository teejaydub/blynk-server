package cc.blynk.server.application.handlers.sharing;

import cc.blynk.server.Holder;
import cc.blynk.server.application.handlers.main.logic.AddPushLogic;
import cc.blynk.server.application.handlers.main.logic.AppSyncLogic;
import cc.blynk.server.application.handlers.main.logic.LoadSharedProfileGzippedLogic;
import cc.blynk.server.application.handlers.main.logic.dashboard.device.GetDevicesLogic;
import cc.blynk.server.application.handlers.main.logic.reporting.DeleteEnhancedGraphDataLogic;
import cc.blynk.server.application.handlers.main.logic.reporting.GetEnhancedGraphDataLogic;
import cc.blynk.server.application.handlers.main.logic.reporting.GetGraphDataLogic;
import cc.blynk.server.application.handlers.sharing.auth.AppShareStateHolder;
import cc.blynk.server.application.handlers.sharing.logic.HardwareAppShareLogic;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.server.core.session.StateHolderBase;
import cc.blynk.server.core.stats.GlobalStats;
import cc.blynk.server.handlers.BaseSimpleChannelInboundHandler;
import cc.blynk.server.handlers.common.LogoutLogic;
import cc.blynk.server.handlers.common.PingLogic;
import io.netty.channel.ChannelHandlerContext;

import static cc.blynk.server.core.protocol.enums.Command.ADD_PUSH_TOKEN;
import static cc.blynk.server.core.protocol.enums.Command.APP_SYNC;
import static cc.blynk.server.core.protocol.enums.Command.DELETE_ENHANCED_GRAPH_DATA;
import static cc.blynk.server.core.protocol.enums.Command.GET_DEVICES;
import static cc.blynk.server.core.protocol.enums.Command.GET_ENHANCED_GRAPH_DATA;
import static cc.blynk.server.core.protocol.enums.Command.GET_GRAPH_DATA;
import static cc.blynk.server.core.protocol.enums.Command.HARDWARE;
import static cc.blynk.server.core.protocol.enums.Command.LOAD_PROFILE_GZIPPED;
import static cc.blynk.server.core.protocol.enums.Command.LOGOUT;
import static cc.blynk.server.core.protocol.enums.Command.PING;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
public class AppShareHandler extends BaseSimpleChannelInboundHandler<StringMessage> {

    public final AppShareStateHolder state;
    private final HardwareAppShareLogic hardwareApp;
    private final GetGraphDataLogic graphData;
    private final GetEnhancedGraphDataLogic enhancedGraphDataLogic;
    private final DeleteEnhancedGraphDataLogic deleteEnhancedGraphDataLogic;
    private final GlobalStats stats;

    public AppShareHandler(Holder holder, AppShareStateHolder state) {
        super(StringMessage.class, holder.limits);
        this.hardwareApp = new HardwareAppShareLogic(holder, state.userKey.email);
        this.graphData = new GetGraphDataLogic(holder.reportingDao, holder.blockingIOProcessor);
        this.enhancedGraphDataLogic = new GetEnhancedGraphDataLogic(holder.reportingDao, holder.blockingIOProcessor);
        this.deleteEnhancedGraphDataLogic =
                new DeleteEnhancedGraphDataLogic(holder.reportingDao, holder.blockingIOProcessor);
        this.state = state;
        this.stats = holder.stats;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, StringMessage msg) {
        this.stats.incrementAppStat();
        switch (msg.command) {
            case HARDWARE:
                hardwareApp.messageReceived(ctx, state, msg);
                break;
            case LOAD_PROFILE_GZIPPED :
                LoadSharedProfileGzippedLogic.messageReceived(ctx, state, msg);
                break;
            case ADD_PUSH_TOKEN :
                AddPushLogic.messageReceived(ctx, state, msg);
                break;
            case GET_GRAPH_DATA :
                graphData.messageReceived(ctx, state.user, msg);
                break;
            case GET_ENHANCED_GRAPH_DATA :
                enhancedGraphDataLogic.messageReceived(ctx, state.user, msg);
                break;
            case DELETE_ENHANCED_GRAPH_DATA :
                deleteEnhancedGraphDataLogic.messageReceived(ctx, state.user, msg);
                break;
            case GET_DEVICES :
                GetDevicesLogic.messageReceived(ctx, state.user, msg);
                break;
            case PING :
                PingLogic.messageReceived(ctx, msg.id);
                break;
            case APP_SYNC :
                AppSyncLogic.messageReceived(ctx, state, msg);
                break;
            case LOGOUT :
                LogoutLogic.messageReceived(ctx, state.user, msg);
                break;
        }
    }

    @Override
    public StateHolderBase getState() {
        return state;
    }
}
