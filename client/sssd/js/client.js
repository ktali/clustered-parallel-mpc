/*
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

'use strict';

(function() {
  var root = this;

  function assert(cond, msg) {
    if (!msg)
      msg = "";
    console.assert(cond, msg);
  }

  function dateToString(date) {
    var str = date.toDateString() + " " + date.toTimeString().substring(0, 8);
    var millis = date.getMilliseconds();
    var millisStr = "" + millis;
    return str + "." + "0".repeat(3-millisStr.length) + millisStr;
  }

  function runClient(sm, globals, uploadInput) {
    function log(msg) {
      console.log(dateToString(new Date(Date.now())) + " [Client][INFO]  " + msg);
    }

    function debug(msg) {
      console.log(dateToString(new Date(Date.now())) + " [Client][DEBUG] " + msg);
    }

    function logErr(err, msg) {
      if (msg)
        console.error(dateToString(new Date(Date.now())) + " [Client][ERROR] " + msg);
      else
        console.error(dateToString(new Date(Date.now())) + " [Client][ERROR] An error occurred: " );
      if (err)
        console.error(err.stack || err);
    }

    sm.client.logger.log = log;
    sm.client.logger.debug = debug;
    sm.client.logger.error = logErr;

    var hosts = [
      globals.gatewayHosts[0],
      globals.gatewayHosts[1],
      globals.gatewayHosts[2]];

    var gatewayConnection =
      new sm.client.GatewayConnection(hosts, null, function (err) {
        if (err)
          logErr(err);
        assert(false, err.toString());
      });

    log("Opening Gateway connections...");
    gatewayConnection.openConnection(function(err, serverInfos, prngSeed) {
      assert(serverInfos.every((serverInfo) => {
        return serverInfo.instanceUuid === serverInfos[0].instanceUuid; }),
      "Got mismatching instanceUuid-s from gateways."
      );

      sm.prng.init(prngSeed);

      gatewayConnection.gateways.sockets.map(function(s, gatewayNumber) {
        log("Socket ID for Gateway"+(gatewayNumber+1)+": '" +
            s.io.engine.id + "'");
      });

      var checkError = function(err, results, negotiationID) {
        if (!err)
          return;
        logErr(err);
        gatewayConnection.close()
        process.exit(1);
      };

      var uploadPart = function(i) {
        var graph = uploadInput.getGraphPart(i);
        console.log(`partition ${i}, uploading`);
        gatewayConnection.runMpcComputation(
            globals.uploadScriptName,
            graph, 
            function(err, results, negotiationID) {
              checkError(err, results, negotiationID);
              log(`Finished uploading part ${i} of input`);
              if (i + 1 < globals.numParts) {
                log(`Uploading part ${i + 1}`);
                uploadPart(i + 1);
              } else {
                log("All input parts uploaded, finalizing");
                finalizeUpload()
              }
            },
            null,
            {'requestedCliques': 1}
        ); 
      }

      var finalizeUpload = function() {
        gatewayConnection.runMpcComputation(
            globals.finalizeUploadScriptName,
            {"num_cliques": uploadInput.getPublicUint64(globals.numComputationCliques)},
            function(err, results, negotiationID) {
              checkError(err, results, negotiationID);
              log("Inputs finalized! Running computation");
              computation(true);
            },
            null,
            {'requestedCliques': 1}
        );   
      }

      var cliqueIndices = {
        'name': 'clique_idx',
        'values': uploadInput.serializeArray(uploadInput.getConsecutivePublicUint64s(globals.numComputationCliques))
      }

      var iterations = 0;

      var computation = function(first) {
        iterations++;
        gatewayConnection.runMpcComputation(
            globals.computationScriptName,
            {"first_iteration": uploadInput.getPublicBool(first)},
            function(err, results, negotiationID) {
              checkError(err, results, negotiationID);
              log(`Finished iteration ${iterations} of computation!`);
              // Check for vote aborts
              var allAbort = true;
              for (var i = 0; i < globals.numComputationCliques; i++) {
                if (!(`abort-${i}` in results)) {
                  allAbort = false;
                  break;
                }
              }
              if (allAbort) {
                log(`Finished SSSP with all nodes voting to abort after ${iterations} iterations.`);
                gatewayConnection.close();
                return;
              } else {
                log("Running communication");
                communication();
              }
            },
            null,
            {
              'requestedCliques': globals.numComputationCliques,
              'perCliqueArgument': cliqueIndices 
            }
        ); 
      }

      var communication = function() {
        gatewayConnection.runMpcComputation(
            globals.communicationScriptName,
            {'num_cliques': uploadInput.getPublicUint64(globals.numComputationCliques)},
            function(err, results, negotiationID) {
              checkError(err, results, negotiationID);
              log("Finished communication round! Running computation");
              computation(false);
            },
            null,
            {'requestedCliques': 1}
        )
      }
      log("Creating tables");
      gatewayConnection.runMpcComputation(
          globals.createTablesScriptName,
          {}, 
          function(err, results, negotiationID) {
            checkError(err, results, negotiationID);
            log("Finished table creation! Running upload");
            uploadPart(0);
          },
          null,
          {'requestedCliques': 1}
      );
    });
  }

  if (typeof exports !== 'undefined') {
    if (typeof module !== 'undefined' && module.exports) {
      exports = module.exports = runClient;
    }
    exports.runClient = runClient;
  }
  else {
    root.runClient = runClient;
  }

}).call(this);
