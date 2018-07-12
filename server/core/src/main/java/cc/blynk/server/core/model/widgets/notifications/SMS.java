package cc.blynk.server.core.model.widgets.notifications;

import cc.blynk.server.core.model.widgets.NoPinWidget;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 21.03.15.
 */
public class SMS extends NoPinWidget {

    private static final int MAX_SMS_BODY_SIZE = 160;

    public String to;

    public static boolean isWrongBody(String body) {
       return body == null || body.isEmpty() || body.length() > MAX_SMS_BODY_SIZE;
    }

    @Override
    public int getPrice() {
        return 100;
    }
}
