package cc.blynk.server.notifications.sms;

import org.asynchttpclient.AsyncHttpClient;

import java.util.Properties;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 19.03.16.
 */
public class SMSWrapper {

    private final SmsClient client;

    public SMSWrapper(Properties props, AsyncHttpClient httpclient) {
        String service = props.getProperty("sms.service");
        if (service != null && service.contains("messagebird")) {
            client = new MessageBirdClient(props, httpclient);
        } else {
            client = new NexmoClient(props, httpclient);
        }
    }

    public void send(String to, String text) throws Exception {
        client.send(to, text);
    }

}
