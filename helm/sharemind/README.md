# Sharemind Helm Chart

This document walks through the process of installing the Linkerd service-mesh and Linkerd multicluster components to a cluster.
Further, it gives instructions on how to generate trust certificates and link the clusters, allowing for secure mirroring of services.

If properly deployed with Linkerd and the Statefulset is scaled to 6 parallel instances, executing the following commands should give similar results to the following
```
$ kubectl get pods
NAME                                                   READY   STATUS      RESTARTS   AGE
pod/test-sharemind-set-0                               2/2     Running     0          6m44s
pod/test-sharemind-set-1                               2/2     Running     0          3m26s
pod/test-sharemind-set-2                               2/2     Running     0          3m9s
pod/test-sharemind-set-3                               2/2     Running     0          2m55s
pod/test-sharemind-set-4                               2/2     Running     0          2m33s
pod/test-sharemind-set-5                               2/2     Running     0          2m6s
pod/test-sharemind-web-gateway-5cb454b89f-qwcvr        1/1     Running     0          6m44s

$ kubectl get services
NAME                                    TYPE           CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE
service/kubernetes                      ClusterIP      10.96.0.1       <none>        443/TCP          62d
service/test-sharemind-headless         ClusterIP      None            <none>        30000/TCP        6m44s
service/test-sharemind-headless-org-1   ClusterIP      None            <none>        30000/TCP        4m30s
service/test-sharemind-headless-org-2   ClusterIP      None            <none>        30000/TCP        6m39s
service/test-sharemind-set-0-org-1      ClusterIP      10.96.55.110    <none>        30000/TCP        4m21s
service/test-sharemind-set-0-org-2      ClusterIP      10.96.159.210   <none>        30000/TCP        6m30s
service/test-sharemind-set-1-org-1      ClusterIP      10.96.71.192    <none>        30000/TCP        3m9s
service/test-sharemind-set-1-org-2      ClusterIP      10.96.247.166   <none>        30000/TCP        3m9s
service/test-sharemind-set-2-org-1      ClusterIP      10.96.3.217     <none>        30000/TCP        2m55s
service/test-sharemind-set-2-org-2      ClusterIP      10.96.211.2     <none>        30000/TCP        2m55s
service/test-sharemind-set-3-org-1      ClusterIP      10.96.67.219    <none>        30000/TCP        2m35s
service/test-sharemind-set-3-org-2      ClusterIP      10.96.40.147    <none>        30000/TCP        2m32s
service/test-sharemind-set-4-org-1      ClusterIP      10.96.204.222   <none>        30000/TCP        2m6s
service/test-sharemind-set-4-org-2      ClusterIP      10.96.23.201    <none>        30000/TCP        2m5s
service/test-sharemind-set-5-org-1      ClusterIP      10.96.182.164   <none>        30000/TCP        98s
service/test-sharemind-set-5-org-2      ClusterIP      10.96.94.96     <none>        30000/TCP        99s
service/test-sharemind-web-gateway      LoadBalancer   10.96.65.104    172.18.3.1    8080:30205/TCP   6m44s

$ kubectl get deployments
NAME                                              READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/test-sharemind-web-gateway        1/1     1            1           6m44s

$ kubectl get statefulsets
NAME                                  READY   AGE
statefulset.apps/test-sharemind-set   6/6     6m44s
```

### Prerequisites
* Local installation of [Helm3](https://helm.sh/docs/intro/install/)
* A running Kubernetes cluster and access set up via kubectl in the local machine
  * The cluster has to provide a StorageClass for accomodating ReadWriteMany PersistentVolumeClaims, either dynamically or with pre-provisioned PersistentVolumes. Some tips for different cloud provider's clusters are given in [cluster-storage.md](cluster-storage.md)
* Valid Sharemind MPC license in this directory, named `license.p7b`
* A Sharemind server container image in a private registry. 
* The provided [gateway](../../gateway) as a container image based on [this Dockerfile](../../gateway/Dockerfile) in a private registry.
  * If the repository requires authentication, then the corresponding credentials have to be [provided to the Kubernetes cluster in the form of a `regcred` Secret](). A Docker registry Secret could be created as follows:
  ```
  kubectl create secret generic regcred \
    --from-file=.dockerconfigjson=$HOME/.docker/config.json \
    --type=kubernetes.io/dockerconfigjson
  ```

## Sharemind StatefulSet and Gateway

***Any of the following configuration has to be done for each cluster***

#### The `values.yaml` file
Parties' clusters require some independent configuration in terms of discovery and the MPC programs.
The `values.yaml` file allows specifying all of the configurable fields in from one place in the [YAML format](https://yaml.org/spec/).

The base `values.yaml` contains empty fields that need to be filled. Notes regarding the meanings of the values are give as comments. `values-org-{gke|aks|eks}.yaml` demonstrate a filled example configuration for a three party deployment on Google Kubernetes Engine, Azure Kubernetes Service, and Elastic Kubernetes Service respectively.

Some additional important notes about configuration follow.

#### Identifying parties

Sharemind requires each party to have a name. For the current cluster, this name can be set on the `worker.identity.name` field. The same name should be used for specifying the current party as one of the MPC parties of the MPC protocol in other clusters' values files (in `worker.parties[].name` and `worker.shared3p.parties[]`).

Linkerd multicluster requires each cluster to have a dns compliant identifier for service mirroring. Sharemind servers use this value for discovering the neighbouring clique instances. This should be set on fields `worker.parties[].mirrorDesignation` for each party, and is also needed in the [Linkerd and multicluster setup](../multicluster/README.md).

#### Keys
Sharemind manages authentication and encryption of data in transit using asymmetric encryption.
All clients and servers hold private keys and the public keys of those who they communicate with.
The gateway is also considered a client and holds its own key pair.
Details on how to generate keys for Sharemind components is shown on the [Sharemind Developer Zone website](https://docs.sharemind.cyber.ee/2022.03/installation/client-applications).

Key paths on the local filesystem are required by the `values.yaml` file in several places as `skey` and `pkey`.
Keys of the preconfigured YAML files are given as samples in the [`./keys`](keys) directory; these are suitable for testing only!
As for the parallel Sharemind servers, each party uses a single keypair for all of their parallel instances.

#### Automatic downscaling

Automatic downscaling of Sharemind servers is achieved with the Kubernetes HorizontalPodAutoscaler resource observing a Prometheus instance.
This is an optional feature, as the StatefulSet could be manually scaled down by `kubectl scale statefulset test-sharemind-set --replicas=0` whenever there are no ongoing computations.
However, automatic downscaling may save resources and cost, especially in elastic clusters, where nodes are provisioned on demand.

This feature requires an in-cluster Prometheus instance.
The easiest way to get it would be to install Linkerd's viz extension, after the (Linkerd multicluster steps)[../multicluster/README.md] are completed.
For this, first run
```
$ helm install linkerd-viz -n linkerd-viz --create-namespace linkerd/linkerd-viz
```

Further, metrics collection in the cluster has to be set up
```
$ kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
$ git pull git@github.com:zalando-incubator/kube-metrics-adapter.git
$ patch -u kube-metrics-adapter/docs/deployment.yaml -i autoscaling/kube-metrics-adapter-deployment.patch
$ kubectl apply -f kube-metrics-adapter/docs/
```

The HorizontalPodAutoscaler within this chart should then automatically observe the CPU utilization of the last pod of the server StatefulSet, scaling down if it experiences no utilization.

#### SecreC binaries

SecreC compiled code has to be uploaded to the cluster via a ConfigMap.
A Kustomization script for creating the ConfigMap has been provided in the [secrec](../../secrec/kustomization.yaml) directory. New binaries can be added to it if needed.
It can be run from within its directory as
```
kubectl create -k .
```

### Installing

The system is installed in the currently active cluster context using
```
helm install test . --values values.yaml
```

### Uninstalling

```
helm uninstall test
```
