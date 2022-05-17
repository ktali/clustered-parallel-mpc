/*
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

package ee.cybernetica.sharemind.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ee.cybernetica.sharemind.gateway.*;
import ee.cybernetica.sharemind.gateway.internal.RelayData;
import ee.cybernetica.sharemind.gateway.internal.ServerInfo;
import ee.cybernetica.sharemind.gateway.internal.SharemindValueSerializer;
import io.socket.emitter.Emitter;
import io.socket.socketio.server.SocketIoSocket;
import org.apache.commons.cli.CommandLine;
import org.json.JSONObject;
import sun.misc.Signal;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
  private SharemindLogger logger;
  private List<SharemindWebGateway> gateways;
  private GatewaySocketServer server;
  private final Gson gson = new GsonBuilder()
      .registerTypeAdapter(SharemindValue.class, new SharemindValueSerializer())
      .create();

  public byte[] secureRandom(int bytes) {
    byte[] rand = new byte[bytes];
    new SecureRandom().nextBytes(rand);
    return rand;
  }

  public static void main(String[] args) throws Exception {
    CommandLine cl = Cli.getCommandLine(args);
    if (cl != null) {
      new Main().run(
          cl.getOptionValue('c', "gateway.cfg"),
          cl.getOptionValue('i', "scriptinfo"),
          Integer.parseInt(cl.getOptionValue('p', "8080")),
          Integer.parseInt(cl.getOptionValue('m', "10"))
      );
    }
  }

  public void run(String configPath,
                  String scriptInfoPath,
                  int port,
                  int maxCliques) {
    logger = new SharemindLogger();
    server = new GatewaySocketServer(port, logger);
    gateways = new ArrayList<>();

    ClientConfiguration config = new ClientConfiguration(10000);

    Signal.handle(new Signal("TERM"), signal -> {
      logger.logInfo("Caught SIGTERM, closing gateway...");
      stop(1);
    });
    Signal.handle(new Signal("INT"), signal -> {
      logger.logInfo("Caught SIGINT, closing gateway...");
      stop(1);
    });

    Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
      logger.logError("Uncaught exception: ", throwable);
      stop(1);
    });

    ObjectMapper om = new ObjectMapper(new YAMLFactory());
    Map<String, SecreCScriptInfo> scriptsInfoMap = new HashMap<>();
    File[] scriptInfoFiles = new File(scriptInfoPath).listFiles();
    if (scriptInfoFiles == null) {
      logger.logError(String.format("Error reading scriptinfo directory '%s'", scriptInfoPath));
      return;
    }
    for (File scriptInfoFile : scriptInfoFiles) {
      if (scriptInfoFile.isFile() && scriptInfoFile.getName().endsWith(".yaml")) {
        try {
          scriptsInfoMap.put(scriptInfoFile.getName().replace(".yaml", ""), om.readValue(scriptInfoFile, SecreCScriptInfo.class));
        } catch (IOException e) {
          logger.logError(String.format("Error reading script info file %s", scriptInfoFile.getName()), e);
        }
      }
    }
    if (scriptsInfoMap.isEmpty()) {
      logger.logError(String.format("No script info files parsed from scriptinfo directory '%s'", scriptInfoPath));
      return;
    }

    server.mSocketIoServer.namespace("/").on("connection", args -> {

      ConcurrentHashMap<Integer, Queue<RelayData>> startMpcProcessNegotiationResults =
          new ConcurrentHashMap<>();  // RelayDatas
      ConcurrentHashMap<Integer, Queue<Boolean>> relayDataResults =
          new ConcurrentHashMap<>();  // successful relaydata acknowledgements
      ConcurrentHashMap<Integer, Queue<Map<String, SharemindValue>>> mpcProcessResults =
          new ConcurrentHashMap<>();  // mpc process results
      ConcurrentHashMap<Integer, Queue<Map<String, SharemindValue>>> spcProcessResults =
          new ConcurrentHashMap<>();  // mpc process results
      ConcurrentHashMap<Integer, Integer> processCliques =
          new ConcurrentHashMap<>();

      SocketIoSocket client = (SocketIoSocket) args[0];
      logger.logInfo(String.format("Client %s connected.", client.getId()));

      Emitter clientIngress = new LoggingEmitter(logger); // one per client, given to any parallel clique
      gateways.stream()
          .map(gw -> gw.handleNewClient(clientIngress, scriptsInfoMap, config))
          .forEach(clientEvents -> registerGatewayEvents(clientEvents, client, startMpcProcessNegotiationResults,
              relayDataResults, spcProcessResults, mpcProcessResults, processCliques));

      client.on("startMpcProcessNegotiation", mpcArgs -> {
        logger.logDebug("startMpcProcessNegotiation");
        int negotiationID = (int) mpcArgs[1];
        SocketIoSocket.ReceivedByLocalAcknowledgementCallback callback;
        try {
          callback = (SocketIoSocket.ReceivedByLocalAcknowledgementCallback) mpcArgs[3];
        } catch (Exception e) {
          logger.logError("Acquiring callback from startMpcProcessNegotiation failed: ", e);
          return;
        }
        logger.logDebug(String.format("startMpcProcessNegotiation extraArgs: %s", mpcArgs[2]));
        try {
          ParallelExecutionRequest parallelExecutionRequest = gson.fromJson(mpcArgs[2].toString(), ParallelExecutionRequest.class);
          logger.logDebug(String.format("Creating entries for client's negotiation with id %d", negotiationID));
          startMpcProcessNegotiationResults.put(negotiationID, new ArrayBlockingQueue<>(parallelExecutionRequest.requestedCliques));
          relayDataResults.put(negotiationID, new ArrayBlockingQueue<>(parallelExecutionRequest.requestedCliques));
          mpcProcessResults.put(negotiationID, new ArrayBlockingQueue<>(parallelExecutionRequest.requestedCliques));
          spcProcessResults.put(negotiationID, new ArrayBlockingQueue<>(parallelExecutionRequest.requestedCliques));
          processCliques.put(negotiationID, parallelExecutionRequest.requestedCliques);
          int numCreatedGateways = processParallelExecutionRequest(
              parallelExecutionRequest,
              configPath,
              maxCliques
          );
          gateways.stream()
              .skip(gateways.size() - numCreatedGateways)
              .map(gw -> gw.handleNewClient(clientIngress, scriptsInfoMap, config))
              .forEach(clientEvents -> registerGatewayEvents(clientEvents, client, startMpcProcessNegotiationResults,
                  relayDataResults, spcProcessResults, mpcProcessResults, processCliques));
        } catch (Exception e) {
          logger.logError(String.format("Error processing parallel execution request: %s", e.getMessage()));
          callback.sendAcknowledgement(String.format("Error processing parallel execution request: %s", e.getMessage()));
          return;
        }
        clientIngress.emit("startMpcProcessNegotiation", mpcArgs);
      });
      client.on("relayData", mpcArgs -> {
        clientIngress.emit("relayData", mpcArgs);
        logger.logDebug("Passed on relayData to gateway");
      });
      client.on("startMpcProcess", mpcArgs -> {
        clientIngress.emit("startMpcProcess", mpcArgs);
        logger.logDebug("Passed on startMpcProcess to gateway");
      });
      client.on("startSpcProcess", mpcArgs -> {
        clientIngress.emit("startSpcProcess", mpcArgs);
        logger.logDebug("Passed on startSpcProcess to gateway");
      });

      client.on("close", msg -> {
        logger.logInfo(String.format("Client connection closed:%s", msg[0]));
        clientIngress.emit("disconnect");
        logger.logDebug("Active process negotiations removed.");
      });

      logger.logDebug("Before trying to send serverInfo");
      try {
        client.send("serverInfo", new Object[]{
            new JSONObject(new ServerInfo("PARALLEL-INSTANCE", "PARALLEL-SERVER")),
            secureRandom(32)
        }, _args -> {
          logger.logDebug(String.format("[%s] Server info sent.", client.getId()));
        });
      } catch (Exception e) {
        logger.logError(String.format("[%s] Error while sending serverInfo", client.getId()), e);
      }
    });


    logger.logInfo("Running server...");
    server.startServer();
    logger.logInfo(String.format("Listening on port %d", port));
  }

  private void registerGatewayEvents(ClientEventsEmitter clientEvents,
                                     SocketIoSocket client,
                                     ConcurrentHashMap<Integer, Queue<RelayData>> startMpcProcessNegotiationResults,
                                     ConcurrentHashMap<Integer, Queue<Boolean>> relayDataResults,
                                     ConcurrentHashMap<Integer, Queue<Map<String, SharemindValue>>> spcProcessResults,
                                     ConcurrentHashMap<Integer, Queue<Map<String, SharemindValue>>> mpcProcessResults,
                                     ConcurrentHashMap<Integer, Integer> processCliques) {
    clientEvents.on("acknowledgeError", mpcArgs -> {
      SocketIoSocket.ReceivedByLocalAcknowledgementCallback callback =
          (SocketIoSocket.ReceivedByLocalAcknowledgementCallback) mpcArgs[0];
      callback.sendAcknowledgement(mpcArgs[1]);
    });
    clientEvents.on("acknowledge", mpcArgs -> {
      int negotiationID = (int) mpcArgs[1];
      logger.logDebug(String.format("Trying to send back acknowledge, NID: %d", negotiationID));
      relayDataResults.get(negotiationID).offer(true);
      if (relayDataResults.get(negotiationID).size() == processCliques.get(negotiationID)) {
        SocketIoSocket.ReceivedByLocalAcknowledgementCallback callback =
            (SocketIoSocket.ReceivedByLocalAcknowledgementCallback) mpcArgs[0];
        callback.sendAcknowledgement();
        relayDataResults.get(negotiationID).clear();
      }
    });
    clientEvents.on("acknowledgeStartMpcProcessNegotiationSuccess", mpcArgs -> {
      logger.logDebug("Trying to send back relaydata");
      int negotiationID = (int) mpcArgs[2];
      startMpcProcessNegotiationResults.get(negotiationID).offer((RelayData) mpcArgs[1]);
      logger.logInfo(String.format("processnegotiationresults: %d, cliques needed: %d", startMpcProcessNegotiationResults.get(negotiationID).size(), processCliques.get(negotiationID)));
      if (startMpcProcessNegotiationResults.get(negotiationID).size() == processCliques.get(negotiationID)) {
        logger.logDebug("All cliques negotiation success");
        SocketIoSocket.ReceivedByLocalAcknowledgementCallback callback =
            (SocketIoSocket.ReceivedByLocalAcknowledgementCallback) mpcArgs[0];
        callback.sendAcknowledgement("", gson.toJson(startMpcProcessNegotiationResults.get(negotiationID).toArray()));
        startMpcProcessNegotiationResults.remove(negotiationID);
      }
    });
    clientEvents.on("acknowledgeFinishMpcProcess", mpcArgs -> {
      logger.logDebug("Trying to send back mpc results");
      int negotiationID = (int) mpcArgs[2];
      mpcProcessResults.get(negotiationID).offer((Map<String, SharemindValue>) mpcArgs[1]);
      if (mpcProcessResults.get(negotiationID).size() == processCliques.get(negotiationID)) {
        Map<String, SharemindValue> mergedResults = new HashMap<>();
        for (Map<String, SharemindValue> mpcProcessResult : mpcProcessResults.get(negotiationID)) {
          mergedResults.putAll(mpcProcessResult);
        }
        SocketIoSocket.ReceivedByLocalAcknowledgementCallback callback = (SocketIoSocket.ReceivedByLocalAcknowledgementCallback) mpcArgs[0];
        callback.sendAcknowledgement("", new JSONObject(gson.toJson(mergedResults)));
        mpcProcessResults.remove(negotiationID);
      }
    });
    clientEvents.on("acknowledgeFinishSpcProcess", spcArgs -> {
      logger.logDebug("Trying to send back spc results");
      int negotiationID = (int) spcArgs[2];
      spcProcessResults.get(negotiationID).add((Map<String, SharemindValue>) spcArgs[1]);
      if (spcProcessResults.get(negotiationID).size() == processCliques.get(negotiationID)) {
        Map<String, SharemindValue> mergedResults = new HashMap<>();
        for (Map<String, SharemindValue> spcProcessResult : spcProcessResults.get(negotiationID)) {
          mergedResults.putAll(spcProcessResult);
        }
        SocketIoSocket.ReceivedByLocalAcknowledgementCallback callback = (SocketIoSocket.ReceivedByLocalAcknowledgementCallback) spcArgs[0];
        callback.sendAcknowledgement("", gson.toJson(mergedResults));
        spcProcessResults.remove(negotiationID);
      }
    });
    clientEvents.on("disconnectClient", args1 -> {
      client.off();
      client.disconnect(true);
    });
    clientEvents
        .on("log", msg -> logger.logInfo(String.format("[%s] %s", client.getId(), msg[0])))
        .on("logErr", msg -> {
          if (msg.length == 1)
            logger.logError(String.format("[%s] %s", client.getId(), msg[0]));
          else
            logger.logError(String.format("[%s] %s", client.getId(), msg[0]), (Throwable) msg[1]);
        })
        .on("debug", msg -> logger.logDebug(String.format("[%s] %s", client.getId(), msg[0])));
  }

  public int processParallelExecutionRequest(ParallelExecutionRequest request, String configTemplatePath, int maxCliques) throws SharemindException {
    logger.logInfo(String.format("Negotiation requested %d parallel processes", request.requestedCliques));
    if (request.requestedCliques > maxCliques) {
      throw new SharemindException("Parallel process request rejected: too many cliques requested");
    }
    int numGatewaysPre = gateways.size();
    if (gateways.size() < request.requestedCliques) {
      try {
        K8sClient.getInstance().scaleStatefulSet(request.requestedCliques);
      } catch (Exception e) {
        logger.logError("Failed to request additional replicas", e);
        throw new SharemindException(e.getMessage());
      }
      while (gateways.size() < request.requestedCliques) {
        int i = gateways.size();
        SharemindWebGateway gw = newGatewayInstance(
            configTemplatePath.replace(".cfg", String.format("%d.cfg", gateways.size())),
            (short) gateways.size()
        );
        logger.logInfo(String.format("Connecting gateway [%d] to Sharemind application server ...", i));
        try {
          gw.connect();
        } catch (SharemindException e) {
          logger.logError(String.format("Error connecting gateway [%d] to Sharemind", i), e);
          throw e;
        }
        gateways.add(gw);
      }
    }
    return request.requestedCliques - numGatewaysPre;
  }

  private SharemindWebGateway newGatewayInstance(String configPath, short cliqueIdx) {
    logger.logInfo(String.format("Initializing gateway [%s] ...", cliqueIdx));
    SharemindWebGateway gateway;
    try {
      gateway = new SharemindWebGateway(configPath, cliqueIdx, logger);
    } catch (SharemindException e) {
      logger.logError(String.format("Error initializing gateway [%s]", cliqueIdx), e);
      throw e;
    }
    logger.logInfo(String.format("Gateway [%s] initialized.", cliqueIdx));
    return gateway;
  }

  public void closeSocketServer() {
    if (server != null) {
      logger.logInfo("Closing server...");
      server.stopServer();
      logger.logInfo("Closed listening server.");
    }
  }

  public void closeWebGateways() {
    gateways.forEach(gateway -> {
      logger.logInfo("Closing gateway...");
      try {
        gateway.close();
      } catch (SharemindException e) {
        logger.logError("Error when closing WebGateway object: ", e);
      }
      logger.logInfo("Disconnected from Sharemind and closed WebGateway object.");
    });
  }

  public void stop(int rv) {
    closeWebGateways();
    closeSocketServer();
    System.exit(rv);
  }
}
