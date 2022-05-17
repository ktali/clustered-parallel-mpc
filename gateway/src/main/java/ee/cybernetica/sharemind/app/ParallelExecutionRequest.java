/*
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

package ee.cybernetica.sharemind.app;

// POJO
public class ParallelExecutionRequest {
  public final int requestedCliques;

  public ParallelExecutionRequest(int requestedCliques) {
    this.requestedCliques = requestedCliques;
  }
}
