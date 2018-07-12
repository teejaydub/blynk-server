package cc.blynk.server.notifications.sms;

/* SMS client - abstract interface to cover multiple SMS providers.
*/
public interface SmsClient {

    void send(String to, String body) throws Exception;

}
