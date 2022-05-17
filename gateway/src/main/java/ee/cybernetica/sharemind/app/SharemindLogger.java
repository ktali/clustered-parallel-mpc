/*
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

package ee.cybernetica.sharemind.app;

import ee.cybernetica.sharemind.gateway.SharemindLogAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharemindLogger implements SharemindLogAppender {
    private final Logger logger;

    public SharemindLogger() {
        logger = LoggerFactory.getLogger(SharemindLogger.class);
    }

    private String logPrefix() {
        return "[GW]";
    }

    @Override
    public void logInfo(String msg) {
        logger.info(logPrefix() + "[INFO] " + msg);
    }

    @Override
    public void logDebug(String msg) {
        logger.debug(logPrefix() + "[DEBUG] " + msg);
    }

    @Override
    public void logError(String msg, Throwable t) {
        logger.error(logPrefix() + "[ERROR] " + msg, t);
    }

    @Override
    public void logError(String msg) {
        logger.error(logPrefix() + "[ERROR] " + msg);
    }
}
