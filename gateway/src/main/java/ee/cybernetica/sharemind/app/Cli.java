/*
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

package ee.cybernetica.sharemind.app;

import org.apache.commons.cli.*;

public class Cli {
  public static CommandLine getCommandLine(String[] args) throws ParseException {
    Options options = new Options()
        .addOption(Option.builder("h")
            .longOpt("help")
            .desc("Print this help")
            .build())
        .addOption(Option.builder("p")
            .longOpt("port")
            .hasArg()
            .desc("The port that the gateway socket will listen on.")
            .build())
        .addOption(Option.builder("c")
            .longOpt("config")
            .hasArg()
            .desc("The path to the configuration file.")
            .build())
        .addOption(Option.builder("s")
            .longOpt("servers")
            .numberOfArgs(2)
            .valueSeparator(',')
            .desc("The organizations' Sharemind application servers' names participating " +
                "in MPC computation")
            .build())
        .addOption(Option.builder("m")
            .longOpt("max-cliques")
            .hasArg()
            .desc("The maximum number of cliques that can be started.")
            .build())
        .addOption(Option.builder("i")
            .longOpt("script-info")
            .hasArg()
            .desc("Directory containing '<SecreC program>.yaml' declarations.")
            .build());

    CommandLine cl;
    try {
      cl = new DefaultParser().parse(options, args);
    } catch (ParseException e) {
      new HelpFormatter().printHelp(args[0], options, true);
      throw e;
    }

    if (cl.hasOption('h')) {
      new HelpFormatter().printHelp(args[0], options, true);
      return null;
    }

    return cl;
  }
}
