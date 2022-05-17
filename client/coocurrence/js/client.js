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

    // Override Client API default log functions
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

      var runUploadPart = function(i) {
        var inputPart = uploadInput.getInputPart(i);
        log(`partition ${i}, uploading`);
        gatewayConnection.runMpcComputation(
            globals.uploadScriptName,
            inputPart,
            function(err, results, negotiationID) {
              checkError(err, results, negotiationID);
              if (i + 1 < globals.numParts)
                runUploadPart(i + 1);
              else
                runSplitInput();
            },
            null,
            {'requestedCliques': 1}
        ); 
      }

      var runSplitInput = function() {
        log("Splitting input between Map processors");
        gatewayConnection.runMpcComputation(
          globals.splitInputScriptName,
          {"num_cliques": uploadInput.getPublicUint64(globals.numMapCliques)},
          function(err, results, negotiationID) {
            checkError(err, results, negotiationID);
            log("Input splitting finished successfully! running Map.");
            runMap();
          }, 
          null, 
          {"requestedCliques": 1}
        );
      }

      //var cliqueIndices = {
      //  'name': 'clique_idx',
      //  'values': uploadInput.serializeArray(uploadInput.getConsecutivePublicUint64s(3))
      //}

      var runMap = function() {
        gatewayConnection.runMpcComputation(
          globals.mapScriptName,
          {
            "num_cliques": uploadInput.getPublicUint64(globals.numMapCliques),
            "stripe_length": uploadInput.getPublicUint64(globals.stripeLength)
          },
          function(err, results, negotiationID) {
            checkError(err, results, negotiationID);
            log("Map finished successfully! running Partition");
            runPartition();
          },
          null,
          {
            'requestedCliques': globals.numMapCliques,
            'perCliqueArgument': {
              'name': 'clique_idx',
              'values': uploadInput.serializeArray(uploadInput.getConsecutivePublicUint64s(globals.numMapCliques))
            }
          }
        );
      }

      var runPartition = function() {
        gatewayConnection.runMpcComputation(
          globals.partitionScriptName,
          {
            "num_map_cliques": uploadInput.getPublicUint64(globals.numMapCliques),
            "num_reduce_cliques": uploadInput.getPublicUint64(globals.numReduceCliques)
          },
          function(err, results, negotiationID) {
            checkError(err, results, negotiationID);
            log("Partition finished successfully! running Reduce");
            runReduce();           
          },
          null,
          {
            'requestedCliques': 1,
          }
        );
      }

      var runReduce = function() {
        gatewayConnection.runMpcComputation(
          globals.reduceScriptName,
          {},
          function(err, results, negotiationID) {
            checkError(err, results, negotiationID);
            log("Reduce finished successfully! Disconnecting");
            gatewayConnection.close();
          },
          null,
          {
            'requestedCliques': globals.numReduceCliques,
            'perCliqueArgument': {
              'name': 'clique_idx',
              'values': uploadInput.serializeArray(uploadInput.getConsecutivePublicUint64s(globals.numReduceCliques))
            } 
          }
        )
      }

      log("Uploading input");
      runUploadPart(0);     
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
