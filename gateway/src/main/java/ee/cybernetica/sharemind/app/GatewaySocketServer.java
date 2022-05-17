/*
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

package ee.cybernetica.sharemind.app;

import io.socket.engineio.server.EngineIoServer;
import io.socket.engineio.server.JettyWebSocketHandler;
import io.socket.socketio.server.SocketIoServer;
import org.eclipse.jetty.http.pathmap.ServletPathSpec;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeFilter;

import javax.servlet.ServletException;
import java.net.URI;
import java.net.URL;


public class GatewaySocketServer {
    private final SharemindLogger logger;
    private final Server server;
    private final EngineIoServer mEngineIoServer;
    public final SocketIoServer mSocketIoServer;

    public GatewaySocketServer(int port, SharemindLogger logger) {
        this.server = new Server(port);
        this.mEngineIoServer = new EngineIoServer();
        this.mSocketIoServer = new SocketIoServer(mEngineIoServer);
        this.logger = logger;

        configureServer();
    }

    void configureServer() {
        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        handler.setContextPath("/");
        ServletHolder servletHolder = new ServletHolder(new GatewaySocketServlet(mEngineIoServer, logger));
        servletHolder.setAsyncSupported(true);
        handler.addServlet(servletHolder, "/socket.io/*");

        try {
            WebSocketUpgradeFilter webSocketUpgradeFilter = WebSocketUpgradeFilter.configure(handler);
            webSocketUpgradeFilter.addMapping(new ServletPathSpec("/socket.io/*"),
                    (req, resp) -> new JettyWebSocketHandler(mEngineIoServer));
        } catch (ServletException ex) {
            ex.printStackTrace();
        }

        server.setHandler(handler);
    }

    void startServer() {
        try {
            server.start();
            server.join();
        } catch (Exception e) {
            logger.logError("Failed to start server: ", e);
        }
    }

    void stopServer() {
        try {
            server.stop();
        } catch (Exception e) {
            logger.logError("Failed to stop server: ", e);
        }
    }
}
