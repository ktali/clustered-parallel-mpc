/*
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

'use strict';

var sm = require('sharemind-web-client');
var uploadInput = require('./input.js');
var globals = require('./globals.js');
var runClient = require('./client.js');


process.on("SIGTERM", function () {
  console.log("Caught SIGTERM, closing client...");
  process.exit(1);
});

process.on("SIGINT", function () {
  console.log("Caught SIGINT, closing client...");
  process.exit(1);
});

process.on('uncaughtException', function(err) {
  console.error("Uncaught exception:");
  console.error(err);
  process.exit(1);
});

runClient(sm, globals, uploadInput);
