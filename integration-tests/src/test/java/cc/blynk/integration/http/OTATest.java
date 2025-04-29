package cc.blynk.integration.http;

import cc.blynk.integration.BaseTest;
import cc.blynk.integration.https.HttpsAdminServerTest;
import cc.blynk.integration.model.tcp.ClientPair;
import cc.blynk.integration.model.tcp.TestHardClient;
import cc.blynk.server.api.http.HttpAPIServer;
import cc.blynk.server.api.http.HttpsAPIServer;
import cc.blynk.server.application.AppServer;
import cc.blynk.server.core.BaseServer;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.device.Device;
import cc.blynk.server.core.model.serialization.JsonParser;
import cc.blynk.server.core.protocol.model.messages.ResponseMessage;
import cc.blynk.server.core.protocol.model.messages.hardware.BlynkInternalMessage;
import cc.blynk.server.hardware.HardwareServer;
import cc.blynk.utils.FileUtils;
import cc.blynk.utils.SHA256Util;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Base64;

import static cc.blynk.integration.IntegrationBase.b;
import static cc.blynk.integration.IntegrationBase.initAppAndHardPair;
import static cc.blynk.integration.IntegrationBase.ok;
import static cc.blynk.server.core.protocol.enums.Command.BLYNK_INTERNAL;
import static cc.blynk.server.core.protocol.enums.Response.OK;
import static cc.blynk.server.core.protocol.model.messages.MessageFactory.produce;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 24.12.15.
 */
@RunWith(MockitoJUnitRunner.class)
@Ignore
public class OTATest extends BaseTest {

    private BaseServer httpServer;
    private BaseServer httpsServer;
    private BaseServer hardwareServer;
    private BaseServer appServer;

    private CloseableHttpClient httpclient;
    private String httpsAdminServerUrl;

    private ClientPair clientPair;
    private byte[] auth;

    @After
    public void shutdown() throws Exception {
        httpclient.close();
        httpServer.close();
        httpsServer.close();
        hardwareServer.close();
        appServer.close();
        clientPair.stop();
    }

    @Before
    public void init() throws Exception {
        httpServer = new HttpAPIServer(holder).start();
        httpsServer = new HttpsAPIServer(holder).start();
        hardwareServer = new HardwareServer(holder).start();
        appServer = new AppServer(holder).start();
        httpsAdminServerUrl = String.format("https://localhost:%s/admin", httpsPort);

        String pass = "admin";
        User user = new User();
        user.isSuperAdmin = true;
        user.email = "admin@blynk.cc";
        user.pass = SHA256Util.makeHash(pass, user.email);
        holder.userDao.add(user);

        auth = (user.email + ":" + pass).getBytes();

        // Allow TLSv1 protocol only
        SSLContext sslcontext = initUnsecuredSSLContext();
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext, new HttpsAdminServerTest.MyHostVerifier());
        this.httpclient = HttpClients.custom()
                .setSSLSocketFactory(sslsf)
                .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
                .build();
        clientPair = initAppAndHardPair(tcpAppPort, tcpHardPort, properties);
        clientPair.hardwareClient.reset();
        clientPair.appClient.reset();
    }

    @Test
    public void testInitiateOTA() throws Exception {
        clientPair.appClient.send("getToken 1");
        String token = clientPair.appClient.getBody();

        HttpGet request = new HttpGet(httpsAdminServerUrl + "/ota/start?token=" + token);
        request.setHeader(HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString(auth));

        try (CloseableHttpResponse response = httpclient.execute(request)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("", consumeText(response));
        }

        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(produce(7777, BLYNK_INTERNAL, b("ota http://127.0.0.1:18080/static/ota/firmware_ota.bin"))));
    }

    @Test
    public void testInitiateOTAWithFileName() throws Exception {
        clientPair.appClient.send("getToken 1");
        String token = clientPair.appClient.getBody();

        HttpGet request = new HttpGet(httpsAdminServerUrl + "/ota/start?fileName=test.bin" + "&token=" + token);
        request.setHeader(HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString(auth));

        try (CloseableHttpResponse response = httpclient.execute(request)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("", consumeText(response));
        }

        String expectedResult = "http://127.0.0.1:18080/static/ota/test.bin";
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(produce(7777, BLYNK_INTERNAL, b("ota " + expectedResult))));

        request = new HttpGet(httpsAdminServerUrl + "/ota/start?fileName=test.bin" + "&token=" + token);
        request.setHeader(HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString(auth));

        try (CloseableHttpResponse response = httpclient.execute(request)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("", consumeText(response));
        }
    }

    @Test
    public void testImprovedUploadMethod() throws Exception {
        clientPair.appClient.send("getToken 1");
        String token = clientPair.appClient.getBody();

        HttpPost post = new HttpPost(httpsAdminServerUrl + "/ota/start?token=" + token);
        post.setHeader(HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString(auth));

        String fileName = "test.bin";

        InputStream binFile = OTATest.class.getResourceAsStream("/static/ota/" + fileName);
        ContentBody fileBody = new InputStreamBody(binFile, ContentType.APPLICATION_OCTET_STREAM, fileName);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart("upfile", fileBody);
        HttpEntity entity = builder.build();

        post.setEntity(entity);

        String path;
        try (CloseableHttpResponse response = httpclient.execute(post)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            path = consumeText(response);

            assertNotNull(path);
            assertTrue(path.startsWith("/static"));
            assertTrue(path.endsWith("bin"));
        }

        String responseUrl = "http://127.0.0.1:18080" + path;
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(new BlynkInternalMessage(7777, b("ota " + responseUrl))));

        HttpGet index = new HttpGet("http://localhost:" + httpPort + path);

        try (CloseableHttpResponse response = httpclient.execute(index)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("application/octet-stream", response.getHeaders("Content-Type")[0].getValue());
        }
    }

    @Test
    public void testOTAFailedWhenNoDevice() throws Exception {
        clientPair.appClient.send("getToken 1");
        String token = clientPair.appClient.getBody();

        clientPair.hardwareClient.stop();

        HttpPost post = new HttpPost(httpsAdminServerUrl + "/ota/start?token=" + token);
        post.setHeader(HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString(auth));

        String fileName = "test.bin";

        InputStream binFile = OTATest.class.getResourceAsStream("/static/ota/" + fileName);
        ContentBody fileBody = new InputStreamBody(binFile, ContentType.APPLICATION_OCTET_STREAM, fileName);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart("upfile", fileBody);
        HttpEntity entity = builder.build();

        post.setEntity(entity);

        try (CloseableHttpResponse response = httpclient.execute(post)) {
            assertEquals(400, response.getStatusLine().getStatusCode());
            String error = consumeText(response);

            assertNotNull(error);
            assertEquals("No device in session.", error);
        }
    }

    @Test
    public void testOTAWrongToken() throws Exception {
        HttpPost post = new HttpPost(httpsAdminServerUrl + "/ota/start?token=" + 123);
        post.setHeader(HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString(auth));

        String fileName = "test.bin";

        InputStream binFile = OTATest.class.getResourceAsStream("/static/ota/" + fileName);
        ContentBody fileBody = new InputStreamBody(binFile, ContentType.APPLICATION_OCTET_STREAM, fileName);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart("upfile", fileBody);
        HttpEntity entity = builder.build();

        post.setEntity(entity);

        try (CloseableHttpResponse response = httpclient.execute(post)) {
            assertEquals(400, response.getStatusLine().getStatusCode());
            String error = consumeText(response);

            assertNotNull(error);
            assertEquals("Invalid token.", error);
        }
    }

    @Test
    public void testAuthorizationFailed() throws Exception {
        HttpPost post = new HttpPost(httpsAdminServerUrl + "/ota/start?token=" + 123);
        post.setHeader(HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString("123:123".getBytes()));

        String fileName = "test.bin";

        InputStream binFile = OTATest.class.getResourceAsStream("/static/ota/" + fileName);
        ContentBody fileBody = new InputStreamBody(binFile, ContentType.APPLICATION_OCTET_STREAM, fileName);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart("upfile", fileBody);
        HttpEntity entity = builder.build();

        post.setEntity(entity);

        try (CloseableHttpResponse response = httpclient.execute(post)) {
            assertEquals(403, response.getStatusLine().getStatusCode());
            String error = consumeText(response);

            assertNotNull(error);
            assertEquals("Authentication failed.", error);
        }
    }

    @Test
    public void testImprovedUploadMethodAndCheckOTAStatusForDeviceThatNeverWasOnline() throws Exception {
        clientPair.appClient.send("getToken 1");
        String token = clientPair.appClient.getBody();

        HttpPost post = new HttpPost(httpsAdminServerUrl + "/ota/start?token=" + token);
        post.setHeader(HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString(auth));

        String fileName = "test.bin";

        InputStream binFile = OTATest.class.getResourceAsStream("/static/ota/" + fileName);
        ContentBody fileBody = new InputStreamBody(binFile, ContentType.APPLICATION_OCTET_STREAM, fileName);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart("upfile", fileBody);
        HttpEntity entity = builder.build();

        post.setEntity(entity);

        String path;
        try (CloseableHttpResponse response = httpclient.execute(post)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            path = consumeText(response);

            assertNotNull(path);
            assertTrue(path.startsWith("/static"));
            assertTrue(path.endsWith("bin"));
        }

        String responseUrl = "http://127.0.0.1:18080" + path;
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(new BlynkInternalMessage(7777, b("ota " + responseUrl))));

        clientPair.appClient.send("getDevices 1");
        String response = clientPair.appClient.getBody(2);

        Device[] devices = JsonParser.MAPPER.readValue(response, Device[].class);
        assertNotNull(devices);
        assertEquals(1, devices.length);
        Device device = devices[0];
        assertEquals("admin@blynk.cc", device.deviceOtaInfo.otaInitiatedBy);
        assertEquals(System.currentTimeMillis(), device.deviceOtaInfo.otaInitiatedAt, 5000);
        assertEquals(System.currentTimeMillis(), device.deviceOtaInfo.otaUpdateAt, 5000);

        clientPair.hardwareClient.send("internal " + b("ver 0.3.1 h-beat 10 buff-in 256 dev Arduino cpu ATmega328P con W5100 build 111"));

        device = devices[0];
        assertEquals("admin@blynk.cc", device.deviceOtaInfo.otaInitiatedBy);
        assertEquals(System.currentTimeMillis(), device.deviceOtaInfo.otaInitiatedAt, 5000);
        assertEquals(System.currentTimeMillis(), device.deviceOtaInfo.otaUpdateAt, 5000);
    }

    @Test
    public void testImprovedUploadMethodAndCheckOTAStatusForDeviceThatWasOnline() throws Exception {
        clientPair.hardwareClient.send("internal " + b("ver 0.3.1 h-beat 10 buff-in 256 dev Arduino cpu ATmega328P con W5100 build 111"));
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(ok(1)));

        clientPair.appClient.send("getToken 1");
        String token = clientPair.appClient.getBody();

        HttpPost post = new HttpPost(httpsAdminServerUrl + "/ota/start?token=" + token);
        post.setHeader(HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString(auth));

        String fileName = "test.bin";

        InputStream binFile = OTATest.class.getResourceAsStream("/static/ota/" + fileName);
        ContentBody fileBody = new InputStreamBody(binFile, ContentType.APPLICATION_OCTET_STREAM, fileName);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart("upfile", fileBody);
        HttpEntity entity = builder.build();

        post.setEntity(entity);

        String path;
        try (CloseableHttpResponse response = httpclient.execute(post)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            path = consumeText(response);

            assertNotNull(path);
            assertTrue(path.startsWith("/static"));
            assertTrue(path.endsWith("bin"));
        }

        String responseUrl = "http://127.0.0.1:18080" + path;
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(new BlynkInternalMessage(7777, b("ota " + responseUrl))));

        clientPair.appClient.send("getDevices 1");
        String response = clientPair.appClient.getBody(2);

        Device[] devices = JsonParser.MAPPER.readValue(response, Device[].class);
        assertNotNull(devices);
        assertEquals(1, devices.length);

        Device device = devices[0];
        assertEquals("0.3.1", device.hardwareInfo.version);
        assertEquals(10, device.hardwareInfo.heartbeatInterval);
        assertEquals("111", device.hardwareInfo.build);
        assertEquals("admin@blynk.cc", device.deviceOtaInfo.otaInitiatedBy);
        assertEquals(System.currentTimeMillis(), device.deviceOtaInfo.otaInitiatedAt, 5000);
        assertEquals(System.currentTimeMillis(), device.deviceOtaInfo.otaUpdateAt, 5000);

        clientPair.hardwareClient.send("internal " + b("ver 0.3.1 h-beat 10 buff-in 256 dev Arduino cpu ATmega328P con W5100 build 112"));
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(ok(2)));

        clientPair.appClient.send("getDevices 1");
        response = clientPair.appClient.getBody(3);

        devices = JsonParser.MAPPER.readValue(response, Device[].class);
        assertNotNull(devices);
        assertEquals(1, devices.length);

        device = devices[0];
        assertEquals("0.3.1", device.hardwareInfo.version);
        assertEquals(10, device.hardwareInfo.heartbeatInterval);
        assertEquals("112", device.hardwareInfo.build);
        assertEquals("admin@blynk.cc", device.deviceOtaInfo.otaInitiatedBy);
        assertEquals(System.currentTimeMillis(), device.deviceOtaInfo.otaInitiatedAt, 5000);
        assertEquals(System.currentTimeMillis(), device.deviceOtaInfo.otaUpdateAt, 5000);
    }

    @Test
    public void takeBuildDateFromBinaryFile() throws Exception {
        String fileName = "test.bin";
        Path path = new File("src/test/resources/static/ota/" + fileName).toPath();

        assertEquals("Aug 14 2017 20:31:49", FileUtils.getBuildPatternFromString(path));
    }

    @Test
    public void basicOTAForAllDevices() throws Exception {
        HttpPost post = new HttpPost(httpsAdminServerUrl + "/ota/start");
        post.setHeader(HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString(auth));

        String fileName = "test.bin";

        InputStream binFile = OTATest.class.getResourceAsStream("/static/ota/" + fileName);
        ContentBody fileBody = new InputStreamBody(binFile, ContentType.APPLICATION_OCTET_STREAM, fileName);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart("upfile", fileBody);
        HttpEntity entity = builder.build();

        post.setEntity(entity);

        String path;
        try (CloseableHttpResponse response = httpclient.execute(post)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            path = consumeText(response);

            assertNotNull(path);
            assertTrue(path.startsWith("/static"));
            assertTrue(path.endsWith("bin"));
        }

        String responseUrl = "http://127.0.0.1:18080" + path;
        verify(clientPair.hardwareClient.responseMock, after(500).never()).channelRead(any(), eq(new BlynkInternalMessage(7777, b("ota " + responseUrl))));

        HttpGet index = new HttpGet("http://localhost:" + httpPort + path);

        try (CloseableHttpResponse response = httpclient.execute(index)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("application/octet-stream", response.getHeaders("Content-Type")[0].getValue());
        }
    }

    @Test
    public void testConnectedDeviceGotOTACommand() throws Exception {
        HttpPost post = new HttpPost(httpsAdminServerUrl + "/ota/start");
        post.setHeader(HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString(auth));

        String fileName = "test.bin";

        InputStream binFile = OTATest.class.getResourceAsStream("/static/ota/" + fileName);
        ContentBody fileBody = new InputStreamBody(binFile, ContentType.APPLICATION_OCTET_STREAM, fileName);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart("upfile", fileBody);
        HttpEntity entity = builder.build();

        post.setEntity(entity);

        String path;
        try (CloseableHttpResponse response = httpclient.execute(post)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            path = consumeText(response);
        }

        String responseUrl = "http://127.0.0.1:18080" + path;
        verify(clientPair.hardwareClient.responseMock, after(500).never()).channelRead(any(), eq(new BlynkInternalMessage(7777, b("ota " + responseUrl))));

        clientPair.hardwareClient.send("internal " + b("ver 0.3.1 h-beat 10 buff-in 256 dev Arduino cpu ATmega328P con W5100 build 123"));
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(ok(1)));
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(new BlynkInternalMessage(7777, b("ota " + responseUrl))));
        clientPair.hardwareClient.reset();

        clientPair.appClient.send("getDevices 1");
        String response = clientPair.appClient.getBody();

        Device[] devices = JsonParser.MAPPER.readValue(response, Device[].class);
        assertNotNull(devices);
        assertEquals(1, devices.length);
        assertNotNull(devices[0].deviceOtaInfo);
        assertEquals("admin@blynk.cc", devices[0].deviceOtaInfo.otaInitiatedBy);
        assertEquals(System.currentTimeMillis(), devices[0].deviceOtaInfo.otaInitiatedAt, 5000);
        assertEquals(System.currentTimeMillis(), devices[0].deviceOtaInfo.otaInitiatedAt, 5000);
        assertNotEquals(devices[0].deviceOtaInfo.otaInitiatedAt, devices[0].deviceOtaInfo.otaUpdateAt);
        assertEquals("123", devices[0].hardwareInfo.build);

        clientPair.hardwareClient.send("internal " + b("ver 0.3.1 h-beat 10 buff-in 256 dev Arduino cpu ATmega328P con W5100 build ") + "Aug 14 2017 20:31:49");
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(ok(1)));
        verify(clientPair.hardwareClient.responseMock, after(500).never()).channelRead(any(), eq(new BlynkInternalMessage(7777, b("ota " + responseUrl))));

    }

    @Test
    public void testStopOTA() throws Exception {
        HttpPost post = new HttpPost(httpsAdminServerUrl + "/ota/start");
        post.setHeader(HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString(auth));

        String fileName = "test.bin";

        InputStream binFile = OTATest.class.getResourceAsStream("/static/ota/" + fileName);
        ContentBody fileBody = new InputStreamBody(binFile, ContentType.APPLICATION_OCTET_STREAM, fileName);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart("upfile", fileBody);
        HttpEntity entity = builder.build();

        post.setEntity(entity);

        String path;
        try (CloseableHttpResponse response = httpclient.execute(post)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            path = consumeText(response);

            assertNotNull(path);
            assertTrue(path.startsWith("/static"));
            assertTrue(path.endsWith("bin"));
        }
        String responseUrl = "http://127.0.0.1:18080" + path;

        HttpGet stopOta = new HttpGet(httpsAdminServerUrl + "/ota/stop");
        stopOta.setHeader(HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString(auth));

        try (CloseableHttpResponse response = httpclient.execute(stopOta)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
        }

        clientPair.hardwareClient.send("internal " + b("ver 0.3.1 h-beat 10 buff-in 256 dev Arduino cpu ATmega328P con W5100 build 111"));
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(ok(1)));
        verify(clientPair.hardwareClient.responseMock, never()).channelRead(any(), eq(new BlynkInternalMessage(7777, b("ota " + responseUrl))));
    }

    @Test
    public void basicOTAForNonExistingSingleUser() throws Exception {
        HttpPost post = new HttpPost(httpsAdminServerUrl + "/ota/start?user=dimaxxx@mail.ua");
        post.setHeader(HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString(auth));

        String fileName = "test.bin";

        InputStream binFile = OTATest.class.getResourceAsStream("/static/ota/" + fileName);
        ContentBody fileBody = new InputStreamBody(binFile, ContentType.APPLICATION_OCTET_STREAM, fileName);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart("upfile", fileBody);
        HttpEntity entity = builder.build();

        post.setEntity(entity);

        try (CloseableHttpResponse response = httpclient.execute(post)) {
            assertEquals(400, response.getStatusLine().getStatusCode());
            String er = consumeText(response);
            assertNotNull(er);
            assertEquals("Requested user not found.", er);
        }
    }

    @Test
    public void basicOTAForSingleUser() throws Exception {
        HttpPost post = new HttpPost(httpsAdminServerUrl + "/ota/start?user=dima@mail.ua");
        post.setHeader(HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString(auth));

        String fileName = "test.bin";

        InputStream binFile = OTATest.class.getResourceAsStream("/static/ota/" + fileName);
        ContentBody fileBody = new InputStreamBody(binFile, ContentType.APPLICATION_OCTET_STREAM, fileName);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart("upfile", fileBody);
        HttpEntity entity = builder.build();

        post.setEntity(entity);

        String path;
        try (CloseableHttpResponse response = httpclient.execute(post)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            path = consumeText(response);

            assertNotNull(path);
            assertTrue(path.startsWith("/static"));
            assertTrue(path.endsWith("bin"));
        }

        String responseUrl = "http://127.0.0.1:18080" + path;
        verify(clientPair.hardwareClient.responseMock, after(500).never()).channelRead(any(), eq(new BlynkInternalMessage(7777, b("ota " + responseUrl))));

        clientPair.appClient.send("getToken 1");
        String token = clientPair.appClient.getBody();

        TestHardClient newHardwareClient = new TestHardClient("localhost", tcpHardPort);
        newHardwareClient.start();
        newHardwareClient.send("login " + token);
        verify(newHardwareClient.responseMock, timeout(1000)).channelRead(any(), eq(new ResponseMessage(1, OK)));
        newHardwareClient.reset();

        newHardwareClient.send("internal " + b("ver 0.3.1 h-beat 10 buff-in 256 dev Arduino cpu ATmega328P con W5100 build 111"));
        verify(newHardwareClient.responseMock, timeout(500)).channelRead(any(), eq(ok(1)));
        verify(newHardwareClient.responseMock, timeout(500)).channelRead(any(), eq(new BlynkInternalMessage(7777, b("ota " + responseUrl))));
    }

    @Test
    public void basicOTAForSingleUserAndNonExistingProject() throws Exception {
        HttpPost post = new HttpPost(httpsAdminServerUrl + "/ota/start?user=dima@mail.ua&project=123");
        post.setHeader(HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString(auth));

        String fileName = "test.bin";

        InputStream binFile = OTATest.class.getResourceAsStream("/static/ota/" + fileName);
        ContentBody fileBody = new InputStreamBody(binFile, ContentType.APPLICATION_OCTET_STREAM, fileName);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart("upfile", fileBody);
        HttpEntity entity = builder.build();

        post.setEntity(entity);

        String path;
        try (CloseableHttpResponse response = httpclient.execute(post)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            path = consumeText(response);

            assertNotNull(path);
            assertTrue(path.startsWith("/static"));
            assertTrue(path.endsWith("bin"));
        }

        String responseUrl = "http://127.0.0.1:18080" + path;
        verify(clientPair.hardwareClient.responseMock, after(500).never()).channelRead(any(), eq(new BlynkInternalMessage(7777, b("ota " + responseUrl))));

        clientPair.appClient.send("getToken 1");
        String token = clientPair.appClient.getBody();

        TestHardClient newHardwareClient = new TestHardClient("localhost", tcpHardPort);
        newHardwareClient.start();
        newHardwareClient.send("login " + token);
        verify(newHardwareClient.responseMock, timeout(1000)).channelRead(any(), eq(new ResponseMessage(1, OK)));
        newHardwareClient.reset();

        newHardwareClient.send("internal " + b("ver 0.3.1 h-beat 10 buff-in 256 dev Arduino cpu ATmega328P con W5100 build 111"));
        verify(newHardwareClient.responseMock, timeout(500)).channelRead(any(), eq(ok(1)));
        verify(clientPair.hardwareClient.responseMock, never()).channelRead(any(), eq(new BlynkInternalMessage(7777, b("ota " + responseUrl))));
    }

    @Test
    public void basicOTAForSingleUserAndExistingProject() throws Exception {
        HttpPost post = new HttpPost(httpsAdminServerUrl + "/ota/start?user=dima@mail.ua&project=My%20Dashboard");
        post.setHeader(HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString(auth));

        String fileName = "test.bin";

        InputStream binFile = OTATest.class.getResourceAsStream("/static/ota/" + fileName);
        ContentBody fileBody = new InputStreamBody(binFile, ContentType.APPLICATION_OCTET_STREAM, fileName);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart("upfile", fileBody);
        HttpEntity entity = builder.build();

        post.setEntity(entity);

        String path;
        try (CloseableHttpResponse response = httpclient.execute(post)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            path = consumeText(response);

            assertNotNull(path);
            assertTrue(path.startsWith("/static"));
            assertTrue(path.endsWith("bin"));
        }

        String responseUrl = "http://127.0.0.1:18080" + path;
        verify(clientPair.hardwareClient.responseMock, after(500).never()).channelRead(any(), eq(new BlynkInternalMessage(7777, b("ota " + responseUrl))));

        clientPair.appClient.send("getToken 1");
        String token = clientPair.appClient.getBody();

        TestHardClient newHardwareClient = new TestHardClient("localhost", tcpHardPort);
        newHardwareClient.start();
        newHardwareClient.send("login " + token);
        verify(newHardwareClient.responseMock, timeout(1000)).channelRead(any(), eq(new ResponseMessage(1, OK)));
        newHardwareClient.reset();

        newHardwareClient.send("internal " + b("ver 0.3.1 h-beat 10 buff-in 256 dev Arduino cpu ATmega328P con W5100 build 111"));
        verify(newHardwareClient.responseMock, timeout(500)).channelRead(any(), eq(ok(1)));
        verify(newHardwareClient.responseMock, timeout(500)).channelRead(any(), eq(new BlynkInternalMessage(7777, b("ota " + responseUrl))));
    }

}
