package cc.blynk.integration.websocket;

import cc.blynk.integration.IntegrationBase;
import cc.blynk.integration.model.tcp.ClientPair;
import cc.blynk.integration.model.websocket.WebSocketClient;
import cc.blynk.server.Holder;
import cc.blynk.server.api.http.HttpAPIServer;
import cc.blynk.server.application.AppServer;
import cc.blynk.server.core.BaseServer;
import cc.blynk.server.core.protocol.model.messages.ResponseMessage;
import cc.blynk.server.hardware.HardwareServer;
import cc.blynk.utils.properties.GCMProperties;
import cc.blynk.utils.properties.MailProperties;
import cc.blynk.utils.properties.SmsProperties;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.server.core.protocol.enums.Response.OK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 13.01.16.
 */
@RunWith(MockitoJUnitRunner.class)
@Ignore
public class AppWebSocketTest extends IntegrationBase {

    private static BaseServer webSocketServer;
    private static BaseServer hardwareServer;
    private static BaseServer appServer;
    private static ClientPair clientPair;
    private static Holder localHolder;

    //web socket ports
    public static int tcpWebSocketPort;

    protected static final Logger log = LogManager.getLogger(AppWebSocketTest.class);

    @AfterClass
    public static void shutdown() throws Exception {
        log.info("Start shutdown");
        webSocketServer.close();
        appServer.close();
        hardwareServer.close();
        clientPair.stop();
        localHolder.close();
        log.info("Finish shutdown");
    }

    @BeforeClass
    public static void init() throws Exception {
        log.info("Start init");
        properties.setProperty("data.folder", getRelativeDataFolder("/profiles"));
        localHolder = new Holder(properties,
                new MailProperties(Collections.emptyMap()),
                new SmsProperties(Collections.emptyMap()),
                new GCMProperties(Collections.emptyMap()),
                false
        );
        tcpWebSocketPort = httpPort;
        webSocketServer = new HttpAPIServer(localHolder).start();
        appServer = new AppServer(localHolder).start();
        hardwareServer = new HardwareServer(localHolder).start();
        clientPair = initAppAndHardPair(tcpAppPort, tcpHardPort, properties);
        log.info("Finish init");
    }

    @Test
    public void testAppWebSocketlogin() throws Exception{
        log.info("Start test");
        WebSocketClient webSocketClient = new WebSocketClient("localhost", tcpWebSocketPort, "/websockets", false);
        webSocketClient.start();
        log.info("Send login");
        webSocketClient.send("login " + DEFAULT_TEST_USER + " 1");
        verify(webSocketClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(1, OK)));
        log.info("Send ping");
        webSocketClient.send("ping");
        verify(webSocketClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(2, OK)));
        log.info("Finish test");
    }

}
