package cc.blynk.server.hardware.handlers.hardware.logic;

import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.auth.Session;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.device.Device;
import cc.blynk.server.core.model.enums.PinType;
import cc.blynk.server.core.model.widgets.notifications.Notification;
import cc.blynk.server.core.processors.EventorProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Sets a virtual pin when a device goes offline,
 * so you can trigger Eventor events to notify the user.
 *
 */
public final class OfflineFlagLogic {

    private static final Logger log = LogManager.getLogger(OfflineFlagLogic.class);

    private OfflineFlagLogic() {
        // not called
    }

    // Sets or clears the offline flag, and triggers events appropriately.
    public static void setOffline(Notification notification, DashBoard dashBoard,
        Device device, User user, Session session, EventorProcessor eventorProcessor, boolean newState) {
        String newStateString = newState ? "1" : "0";

        if (notification.offlineFlagPin != 0) {
            log.trace("Setting the offline flag pin to {}", newStateString);
            long now = System.currentTimeMillis();

            dashBoard.update(device.id, notification.offlineFlagPin, PinType.VIRTUAL, newStateString, now);

            // Have to explicitly trigger the eventor.
            eventorProcessor.process(user, session, dashBoard, device.id,
                notification.offlineFlagPin, PinType.VIRTUAL, newStateString, now);
        }
    }

}
