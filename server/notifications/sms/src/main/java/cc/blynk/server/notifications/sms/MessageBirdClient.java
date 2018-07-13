package cc.blynk.server.notifications.sms;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Param;
import org.asynchttpclient.Response;

import java.util.ArrayList;
import java.util.Properties;

class MBSmsResponse {

    public RecipientList recipientList;

    public static class RecipientList {

        public int totalCount;
        public int totalSentCount;

    }
}

/* An SmsClient that talks to the MessageBird service.
*/
public class MessageBirdClient implements SmsClient {

    // For now, we're expecting just one recipient per message; may allow multiple in future.
    private static final int RECIPIENTS_PER_SEND = 1;

    private static final Logger log = LogManager.getLogger(MessageBirdClient.class);
    private final AsyncHttpClient httpclient;

    private final String key;
    private final String originator;

    private final ObjectReader responseReader = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readerFor(MBSmsResponse.class);

    public MessageBirdClient(Properties props, AsyncHttpClient httpclient) {
        this(props.getProperty("messagebird.key"), props.getProperty("messagebird.originator"), httpclient);
    }

    private MessageBirdClient(String key, String originator, AsyncHttpClient httpclient) {
        log.debug("Using MessageBird client for SMS");
        this.key = key;
        this.originator = originator;
        this.httpclient = httpclient;
    }

    @Override
    public void send(String to, String text) {
        log.debug("SMS send to: {} message: {}", to, text);

        ArrayList<Param> params = new ArrayList<>();
        params.add(new Param("recipients", to));
        params.add(new Param("originator", originator));
        params.add(new Param("body", text));

        httpclient.preparePost("https://rest.messagebird.com/messages")
                .addHeader("Authorization", "AccessKey " + key)
                .setQueryParams(params)
                .execute(new AsyncCompletionHandler<Response>() {
                    @Override
                    public Response onCompleted(org.asynchttpclient.Response response) throws Exception {
                        if (response.getStatusCode() == 200) {
                            MBSmsResponse smsResponse = responseReader.readValue(response.getResponseBody());
                            if (smsResponse.recipientList.totalSentCount != RECIPIENTS_PER_SEND) {
                                log.error("MessageBird reports {} messages sent; expecting {}",
                                    smsResponse.recipientList.totalSentCount, RECIPIENTS_PER_SEND);
                                log.trace("MessageBird response: {}", response);
                            }
                        }
                        return response;
                    }
                });


    }

}
