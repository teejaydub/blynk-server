package cc.blynk.server.core.processors;

import cc.blynk.server.core.BlockingIOProcessor;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.auth.Session;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.enums.PinType;
import cc.blynk.server.core.model.widgets.notifications.Mail;
import cc.blynk.server.core.model.widgets.notifications.Notification;
import cc.blynk.server.core.model.widgets.notifications.Twitter;
import cc.blynk.server.core.model.widgets.others.eventor.Eventor;
import cc.blynk.server.core.model.widgets.others.eventor.Rule;
import cc.blynk.server.core.model.widgets.others.eventor.model.action.BaseAction;
import cc.blynk.server.core.model.widgets.others.eventor.model.action.SetPinAction;
import cc.blynk.server.core.model.widgets.others.eventor.model.action.notification.MailAction;
import cc.blynk.server.core.model.widgets.others.eventor.model.action.notification.NotificationAction;
import cc.blynk.server.core.model.widgets.others.eventor.model.action.notification.NotifyAction;
import cc.blynk.server.core.model.widgets.others.eventor.model.action.notification.TwitAction;
import cc.blynk.server.core.stats.GlobalStats;
import cc.blynk.server.notifications.mail.MailWrapper;
import cc.blynk.server.notifications.push.GCMWrapper;
import cc.blynk.server.notifications.twitter.TwitterWrapper;
import cc.blynk.utils.NumberUtil;
import cc.blynk.utils.validators.BlynkEmailValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.server.core.protocol.enums.Command.EVENTOR;
import static cc.blynk.server.core.protocol.enums.Command.HARDWARE;
import static cc.blynk.utils.StringUtils.PIN_PATTERN;

/**
 * Class responsible for handling eventor logic.
 *
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 24.08.16.
 */
public class EventorProcessor {

    private static final Logger log = LogManager.getLogger(EventorProcessor.class);

    private final GCMWrapper gcmWrapper;
    private final TwitterWrapper twitterWrapper;
    private final MailWrapper mailWrapper;
    private final BlockingIOProcessor blockingIOProcessor;
    private final GlobalStats globalStats;

    public EventorProcessor(GCMWrapper gcmWrapper, MailWrapper mailWrapper, TwitterWrapper twitterWrapper,
                            BlockingIOProcessor blockingIOProcessor, GlobalStats stats) {
        this.gcmWrapper = gcmWrapper;
        this.mailWrapper = mailWrapper;
        this.twitterWrapper = twitterWrapper;
        this.blockingIOProcessor = blockingIOProcessor;
        this.globalStats = stats;
    }

    public void process(User user, Session session, DashBoard dash, int deviceId, byte pin,
                        PinType type, String triggerValue, long now) {
        Eventor eventor = dash.getWidgetByType(Eventor.class);
        if (eventor == null || eventor.rules == null
                || eventor.deviceId != deviceId || !dash.isActive) {
            return;
        }

        double valueParsed = NumberUtil.parseDouble(triggerValue);
        if (valueParsed == NumberUtil.NO_RESULT) {
            return;
        }

        for (Rule rule : eventor.rules) {
            if (rule.isReady(pin, type)) {
                if (rule.isValid(valueParsed)) {
                    if (!rule.isProcessed) {
                        for (BaseAction action : rule.actions) {
                            if (action.isValid()) {
                                if (action instanceof SetPinAction) {
                                    execute(session, dash, deviceId, (SetPinAction) action, now);
                                } else if (action instanceof NotificationAction) {
                                    execute(user, dash, triggerValue, (NotificationAction) action);
                                }
                                globalStats.mark(EVENTOR);
                            }
                        }
                        rule.isProcessed = true;
                    }
                } else {
                    rule.isProcessed = false;
                }
            }
        }
    }

    private void execute(User user, DashBoard dash, String triggerValue, NotificationAction notificationAction) {
        String body = PIN_PATTERN.matcher(notificationAction.message).replaceAll(triggerValue);
        if (notificationAction instanceof NotifyAction) {
            push(gcmWrapper, dash, body);
        } else if (notificationAction instanceof TwitAction) {
            twit(dash, body);
        } else if (notificationAction instanceof MailAction) {
            MailAction mailAction = (MailAction) notificationAction;
            email(user, dash, mailAction.subject, body);
        }
    }

    private void email(User user, DashBoard dash, String subject, String body) {
        Mail mail = dash.getWidgetByType(Mail.class);

        if (mail == null) {
            log.debug("User has no mail widget.");
            return;
        }

        user.checkDailyEmailLimit();

        String to = (mail.to == null || mail.to.isEmpty()) ? user.email : mail.to;

        if (BlynkEmailValidator.isNotValidEmail(to)) {
            log.error("Invalid mail receiver: {}.", to);
            return;
        }

        blockingIOProcessor.execute(() -> {
            try {
                mailWrapper.sendText(to, subject, body);
            } catch (Exception e) {
                log.warn("Error sending email from eventor. From user {}, to : {}. Reason : {}",
                        user.email, to, e.getMessage());
            }
        });
        user.emailMessages++;
    }

    private void twit(DashBoard dash, String body) {
        if (Twitter.isWrongBody(body)) {
            log.debug("Wrong twit body.");
            return;
        }

        Twitter twitterWidget = dash.getWidgetByType(Twitter.class);

        if (twitterWidget == null
                || twitterWidget.token == null
                || twitterWidget.token.isEmpty()
                || twitterWidget.secret == null
                || twitterWidget.secret.isEmpty()) {
            log.debug("User has no access token provided for eventor twit.");
            return;
        }

        blockingIOProcessor.execute(() -> {
            try {
                twitterWrapper.send(twitterWidget.token, twitterWidget.secret, body);
            } catch (Exception e) {
                String errorMessage = e.getMessage();
                if (errorMessage != null && errorMessage.contains("Eventor. Status is a duplicate")) {
                    log.warn("Error sending twit. Reason : {}", e.getMessage());
                }
            }
        });
    }

    public static void push(GCMWrapper gcmWrapper, DashBoard dash, String body) {
        if (Notification.isWrongBody(body)) {
            log.debug("Wrong push body.");
            return;
        }

        Notification widget = dash.getWidgetByType(Notification.class);

        if (widget == null || widget.hasNoToken()) {
            log.debug("User has no access token provided for eventor push.");
            return;
        }

        widget.push(gcmWrapper, body, dash.id);
    }

    private void execute(Session session, DashBoard dash, int deviceId, SetPinAction action, long now) {
        String body = action.makeHardwareBody();
        session.sendMessageToHardware(dash.id, HARDWARE, 888, body, deviceId);
        session.sendToApps(HARDWARE, 888, dash.id, deviceId, body);

        dash.update(deviceId, action.dataStream.pin, action.dataStream.pinType, action.value, now);
    }
}
