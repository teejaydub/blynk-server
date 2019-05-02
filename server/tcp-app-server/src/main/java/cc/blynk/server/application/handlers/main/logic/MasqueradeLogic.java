package cc.blynk.server.application.handlers.main.logic;

import cc.blynk.server.application.handlers.main.auth.AppStateHolder;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.protocol.exceptions.IllegalCommandException;
import cc.blynk.server.core.protocol.exceptions.NotAllowedException;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.server.Holder;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.server.internal.BlynkByteBufUtil.ok;

/* The MASQUERADE command takes a user name (= email address) and logs you in as that user.
    It's only permitted if you have User.profile.subscription.canMasquerade set.
    Useful for customer support.
*/
public final class MasqueradeLogic {

    private static final Logger log = LogManager.getLogger(MasqueradeLogic.class);
    private final Holder holder;

    public MasqueradeLogic(Holder holder) {
        this.holder = holder;
    }

    public void messageReceived(ChannelHandlerContext ctx, AppStateHolder state, StringMessage message) {
        String newUsername = message.body;

        // Check permission
        User user = state.user;
        if (!user.profile.subscription.canMasquerade) {
            throw new NotAllowedException("User " + user.name + " is not permitted to masquerade.");
        }

        // // Find the new user.
        User newUser = holder.userDao.getByName(newUsername, user.appName);
        if (newUser == null) {
            throw new IllegalCommandException("Can't masquerade to nonexistent user " + newUsername + ".");
        }

        log.debug("Masquerading from user {} as {}.", user.name, newUsername);



        ctx.writeAndFlush(ok(message.id), ctx.voidPromise());
    }

}
