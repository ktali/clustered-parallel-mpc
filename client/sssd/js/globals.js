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

  var globals = {
    serverNames: [
      "TestServer1",
      "TestServer2",
      "TestServer3"],
    gatewayHosts: [
     "http://<Party 1 gateway's LoadBalancer address>:8080",
     "http://<Party 2 gateway's LoadBalancer address>:8080",
     "http://<Party 3 gateway's LoadBalancer address>:8080"
    ],
    createTablesScriptName: "create_tables",
    uploadScriptName: "preupload",
    finalizeUploadScriptName: "finalize_upload",
    computationScriptName: "computation",
    communicationScriptName: "communication", 
    numParts: 200,
    numComputationCliques: 6,
    startMpcProcessTimeout: 10000
  };

  if (typeof exports !== 'undefined') {
    if (typeof module !== 'undefined' && module.exports) {
      exports = module.exports = globals;
    }
    exports.globals = globals;
  }
  else {
    root.globals = globals;
  }
}).call(this);
