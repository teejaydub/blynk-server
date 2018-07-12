package cc.blynk.server.notifications.mail;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.util.Properties;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 14.09.16.
 */
public class GMailClient implements MailClient {

    private static final Logger log = LogManager.getLogger(MailWrapper.class);

    private final Session session;
    private final InternetAddress from;

    GMailClient(Properties mailProperties) {
        final String username = mailProperties.getProperty("mail.smtp.username");
        final String password = mailProperties.getProperty("mail.smtp.password");

        log.info("Initializing gmail smtp mail transport. Username : {}. SMTP host : {}:{}",
                username, mailProperties.getProperty("mail.smtp.host"), mailProperties.getProperty("mail.smtp.port"));

        this.session = Session.getInstance(mailProperties, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        try {
            this.from = new InternetAddress(username);
        } catch (AddressException e) {
            throw new RuntimeException("Error initializing MailWrapper." + e.getMessage());
        }
    }

    @Override
    public void sendText(String to, String subj, String body) throws Exception {
        send(to, subj, body, "text/plain; charset=UTF-8");
    }

    @Override
    public void sendHtml(String to, String subj, String body) throws Exception {
        send(to, subj, body, "text/html; charset=UTF-8");
    }

    @Override
    public void sendHtmlWithAttachment(String to, String subj, String body,
                                       QrHolder[] attachmentData) throws Exception {
        MimeMessage message = new MimeMessage(session);
        message.setFrom(from);
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subj, "UTF-8");

        Multipart multipart = new MimeMultipart();

        MimeBodyPart bodyMessagePart = new MimeBodyPart();
        bodyMessagePart.setContent(body, "text/html; charset=UTF-8");

        multipart.addBodyPart(bodyMessagePart);

        attachQRs(multipart, attachmentData);
        attachCSV(multipart, attachmentData);

        message.setContent(multipart);

        Transport.send(message);

        log.debug("Mail sent to {}. Subj: {}", to, subj);
        log.trace("Mail body: {}", body);
    }

    private void attachCSV(Multipart multipart, QrHolder[] attachmentData) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (QrHolder qrHolder : attachmentData) {
            sb.append(qrHolder.token)
            .append(",")
            .append(qrHolder.deviceId)
            .append(",")
            .append(qrHolder.dashId)
            .append("\n");
        }
        MimeBodyPart attachmentsPart = new MimeBodyPart();
        DataSource source = new ByteArrayDataSource(sb.toString(), "text/csv");
        attachmentsPart.setDataHandler(new DataHandler(source));
        attachmentsPart.setFileName("tokens.csv");

        multipart.addBodyPart(attachmentsPart);
    }

    private void attachQRs(Multipart multipart, QrHolder[] attachmentData) throws Exception {
        for (QrHolder qrHolder : attachmentData) {
            MimeBodyPart attachmentsPart = new MimeBodyPart();
            DataSource source = new ByteArrayDataSource(qrHolder.data, "image/jpeg");
            attachmentsPart.setDataHandler(new DataHandler(source));
            attachmentsPart.setFileName(qrHolder.makeQRFilename());
            multipart.addBodyPart(attachmentsPart);
        }
    }

    private void send(String to, String subj, String body, String contentType) throws Exception {
        MimeMessage message = new MimeMessage(session);
        message.setFrom(from);
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subj, "UTF-8");
        message.setContent(body, contentType);

        Transport.send(message);
        log.debug("Mail sent to {}. Subj: {}", to, subj);
        log.trace("Mail body: {}", body);
    }

}
