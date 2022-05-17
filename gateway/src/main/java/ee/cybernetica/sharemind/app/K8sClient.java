/*
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

package ee.cybernetica.sharemind.app;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Singleton used for scaling the Sharemind application server StatefulSet with the Kubernetes client API.
 */
public class K8sClient {

  private static K8sClient instance = null;

  private final KubernetesClient client;

  private K8sClient() {
    this.client = new DefaultKubernetesClient();
  }

  public static K8sClient getInstance() throws IOException {
    if (instance == null) {
      instance = new K8sClient();
    }
    return instance;
  }

  public void scaleStatefulSet(int replicas) {
    client.apps()
        .statefulSets()
        .inNamespace("default")
        .withName("test-sharemind-set")
        .scale(replicas);
    for (int i = 0; i < replicas; i++) {
      Pod pod = client.pods()
          .inNamespace("default")
          .withName(String.format("test-sharemind-set-%d", i))
          .waitUntilReady(30, TimeUnit.SECONDS);
      if (!pod.getStatus().getContainerStatuses().stream().allMatch(ContainerStatus::getReady))
        throw new RuntimeException(String.format("Pod test-sharemind-set-%d containers failed readiness check. Reason: %s ", i, pod.getStatus().getReason()));
    }
  }


}
