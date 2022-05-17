/*
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

'use strict';

if (sm === undefined)
  var sm = require('sharemind-web-client');
if (BigInteger === undefined) 
  var BigInteger = require('jsbn').BigInteger;
if (fs === undefined)
  var fs = require('fs');


(function() {
  var root = this;
  var pub = sm.types.base;
  var priv = sm.types.shared3p;

  function getGraphPart(partition) {
    var args = JSON.parse(fs.readFileSync(`input-${partition}.json`));
    var argsSize = new pub.Uint64Array(1);
    argsSize.set(0, new BigInteger((Object.keys(args).length / 4).toString()));
    var newargs = {"num_nodes": argsSize};
    for (var propName in args) {
      if (/^nid_\d+$/.test(propName)) {
        var val = new pub.Uint32Array(1);
        val.set(0, args[propName]); 
        newargs[propName] = val;
      } else if (/^nid_\d+_state$/.test(propName)) {
        var val = new pub.Uint32Array(1);
        val.set(0, args[propName]);
        newargs[propName] = new priv.Uint32Array(val);
      } else if (/^nid_\d+_(edges|weights)$/.test(propName)) {
        var arr = new pub.Uint32Array(args[propName]);
        newargs[propName] = new priv.Uint32Array(arr);
      }
    }
    return newargs;
  }

  function getPublicBool(b) {
    var val = new pub.BoolArray(1);
    val.set(0, b);
    return val;
  }

  function getPublicUint64(n) {
    var val = new pub.Uint64Array(1);
    val.set(0, new BigInteger(`${n}`));
    return val;
  }

  function getConsecutivePublicUint64s(n) {
    var arr = [];
    for (var i = 0; i < n; i++) {
      arr.push(getPublicUint64(i));
    }
    return arr;
  }

  function serializeArray(arr) { // serialize a public value array that does not pass through the web client's argument processing
    for (var i = 0; i < arr.length; i++) {
      arr[i] = sm.types.serialize(arr[i], 0);
    }
    return arr;
  }

  var uploadInput = {
    getGraphPart: getGraphPart,
    getPublicUint64: getPublicUint64,
    getConsecutivePublicUint64s: getConsecutivePublicUint64s,
    getPublicBool: getPublicBool,
    serializeArray: serializeArray
  };

  if (typeof exports !== 'undefined') {
    if (typeof module !== 'undefined' && module.exports) {
      exports = module.exports = uploadInput;
    }
    exports.uploadInput = uploadInput;
  } else {
    root.uploadInput = uploadInput;
  }


}).call(this);

