/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 29.09.17.
 */
module cc.blynk.server.notifications.sms {
    requires async.http.client;
    requires org.apache.logging.log4j.core;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;

    exports cc.blynk.server.notifications.sms;
}