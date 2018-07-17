package cc.blynk.server.core.protocol.model.messages.appllication;

import cc.blynk.server.core.protocol.model.messages.StringMessage;

import static cc.blynk.server.core.protocol.enums.Command.UPDATE_ACCOUNT;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 */
public class UpdateAccountMessage extends StringMessage {

    public UpdateAccountMessage(int messageId, String body) {
        super(messageId, UPDATE_ACCOUNT, body.length(), body);
    }

    @Override
    public String toString() {
        return "UpdateAccountMessage{" + super.toString() + "}";
    }
}
