package cc.blynk.server.core.protocol.handlers;

import cc.blynk.server.core.protocol.exceptions.BaseServerException;
import cc.blynk.server.core.protocol.exceptions.UnsupportedCommandException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLException;
import java.io.IOException;

import static cc.blynk.server.internal.BlynkByteBufUtil.makeResponse;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/11/2015.
 */
public interface DefaultExceptionHandler {

    Logger log = LogManager.getLogger(DefaultExceptionHandler.class);

    default void handleBaseServerException(ChannelHandlerContext ctx,
                                           BaseServerException baseServerException, int msgId) {
        log.debug(baseServerException.getMessage());
        if (ctx.channel().isWritable()) {
            ctx.writeAndFlush(makeResponse(msgId, baseServerException.errorCode), ctx.voidPromise());
        }
    }

    default void handleGeneralException(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof BaseServerException) {
            BaseServerException baseServerException = (BaseServerException) cause;
            handleBaseServerException(ctx, baseServerException, baseServerException.msgId);
        } else {
            handleUnexpectedException(ctx, cause);
        }
    }

    default void handleUnexpectedException(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof ReadTimeoutException) {
            log.trace("Channel was inactive for a long period. Closing...");
            //channel is already closed here by ReadTimeoutHandler
        } else if (cause instanceof DecoderException) {
            if (cause.getCause() instanceof UnsupportedCommandException) {
                log.debug("Input command is invalid. Closing socket. Reason {}. Address {}",
                        cause.getMessage(), ctx.channel().remoteAddress());
            } else if (cause.getCause() instanceof SSLException) {
                log.debug("Unsecured connection attempt. Channel : {}. Reason : {}",
                        ctx.channel().remoteAddress(), cause.getMessage());
            } else {
                log.error("DecoderException.", cause);
            }
            ctx.close();
        } else if (cause instanceof NotSslRecordException) {
            log.debug("Not secure connection attempt detected. {}. IP {}",
                    cause.getMessage(), ctx.channel().remoteAddress());
            ctx.close();
        } else if (cause instanceof SSLException) {
            log.warn("SSL exception. {}.", cause.getMessage());
            ctx.close();
        } else if (cause instanceof IOException) {
            log.trace("Blynk server IOException.", cause);
        } else {
            String message = cause == null ? "" : cause.getMessage();
            if (message != null && message.contains("OutOfDirectMemoryError")) {
                log.error("OutOfDirectMemoryError!!!");
            } else {
                log.error("Unexpected error! Handler class : {}. Name : {}. Reason : {}. Channel : {}.",
                        ctx.handler().getClass(), ctx.name(), message, ctx.channel());
                log.debug(cause);
            }
        }

    }

}
