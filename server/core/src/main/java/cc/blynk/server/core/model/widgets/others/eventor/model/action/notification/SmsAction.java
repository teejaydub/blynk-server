package cc.blynk.server.core.model.widgets.others.eventor.model.action.notification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/* An action to send an SMS message, using the SMS widget.
*/
public class SmsAction extends NotificationAction {

    @JsonCreator
    public SmsAction(@JsonProperty("message") String message) {
        super(message);
    }

}
