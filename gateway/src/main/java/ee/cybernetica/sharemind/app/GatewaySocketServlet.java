/*
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

package ee.cybernetica.sharemind.app;

import io.socket.engineio.server.EngineIoServer;

import javax.servlet.AsyncContext;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(value = "/engine.io/*", asyncSupported = true)
public class GatewaySocketServlet extends HttpServlet {
    private final EngineIoServer mEngineIoServer;
    private final SharemindLogger logger;

    public GatewaySocketServlet(EngineIoServer mEngineIoServer, SharemindLogger logger) {
        this.mEngineIoServer = mEngineIoServer;
        this.logger = logger;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        resp.addHeader("Access-Control-Allow-Origin", "*");
        final AsyncContext ctxt = req.startAsync();
        ctxt.start(ctxt::complete);
    }

    @Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            mEngineIoServer.handleRequest(new HttpServletRequestWrapper(req) {
                @Override
                public boolean isAsyncSupported() {
                    return true;
                }
            }, res);
        } catch (NullPointerException e) {
            logger.logError("NullPointerException caught in GatewaySocketServlet.service, " + e.getMessage());
        }

    }
}
