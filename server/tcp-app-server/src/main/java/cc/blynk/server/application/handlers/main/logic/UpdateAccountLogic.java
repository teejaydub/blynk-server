package cc.blynk.server.application.handlers.main.logic;

import cc.blynk.server.application.handlers.main.auth.AppStateHolder;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.server.internal.BlynkByteBufUtil.ok;

/* The UPDATE_ACCOUNT command just copies a string to User.profile.account.
    Read it out of the profile when you want its value.
    JSON is suggested but not enforced.
*/
public final class UpdateAccountLogic {

    private static final Logger log = LogManager.getLogger(UpdateAccountLogic.class);

    private UpdateAccountLogic() {

    }

    public static void messageReceived(ChannelHandlerContext ctx, AppStateHolder state, StringMessage message) {
        String newAccount = message.body;
        User user = state.user;

        log.debug("Updating {}'s account to '{}'.", user.name, newAccount);
        user.profile.account = newAccount;

        user.lastModifiedTs = System.currentTimeMillis();
        ctx.writeAndFlush(ok(message.id), ctx.voidPromise());
    }

}
