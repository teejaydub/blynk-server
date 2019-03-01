package cc.blynk.server.core.protocol.model.messages.appllication;

import cc.blynk.server.core.protocol.model.messages.StringMessage;

import static cc.blynk.server.core.protocol.enums.Command.MASQUERADE;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 */
public class MasqueradeMessage extends StringMessage {

    public MasqueradeMessage(int messageId, String body) {
        super(messageId, MASQUERADE, body.length(), body);
    }

    @Override
    public String toString() {
        return "MasqueradeMessage{" + super.toString() + "}";
    }
}
