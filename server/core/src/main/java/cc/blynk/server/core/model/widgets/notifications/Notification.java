package cc.blynk.server.core.model.widgets.notifications;

import cc.blynk.server.core.model.widgets.NoPinWidget;
import cc.blynk.server.notifications.push.GCMWrapper;
import cc.blynk.server.notifications.push.android.AndroidGCMMessage;
import cc.blynk.server.notifications.push.enums.Priority;
import cc.blynk.server.notifications.push.ios.IOSGCMMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 21.03.15.
 */
public class Notification extends NoPinWidget {

    private static final int MAX_PUSH_BODY_SIZE = 255;

    public volatile ConcurrentMap<String, String> androidTokens = new ConcurrentHashMap<>();

    public volatile ConcurrentMap<String, String> iOSTokens = new ConcurrentHashMap<>();

    public boolean notifyWhenOffline;

    public int notifyWhenOfflineIgnorePeriod;

    public byte offlineFlagPin = 0;  // always virtual, so no need to store a PinType
        // 0 means don't set any pin.

    public Priority priority = Priority.normal;

    public String soundUri;

    public static boolean isWrongBody(String body) {
        return body == null || body.isEmpty() || body.length() > MAX_PUSH_BODY_SIZE;
    }

    public boolean hasNoToken() {
        return iOSTokens.size() == 0 && androidTokens.size() == 0;
    }

    @Override
    public int getPrice() {
        return 400;
    }

    public void push(GCMWrapper gcmWrapper, String body, int dashId) {
        if (androidTokens.size() != 0) {
            for (Map.Entry<String, String> entry : androidTokens.entrySet()) {
                gcmWrapper.send(
                        new AndroidGCMMessage(entry.getValue(), priority, body, dashId),
                        androidTokens,
                        entry.getKey()
                );
            }
        }

        if (iOSTokens.size() != 0) {
            for (Map.Entry<String, String> entry : iOSTokens.entrySet()) {
                gcmWrapper.send(new IOSGCMMessage(entry.getValue(), priority, body, dashId),
                        iOSTokens,
                        entry.getKey()
                );
            }
        }
    }
}
