# Linkerd and multicluster installation

This document walks through the process of installing the Linkerd service-mesh and Linkerd multicluster components to a cluster.
Further, it gives instructions on how to generate trust certificates and link the clusters, allowing for secure mirroring of services.

Steps are adapted and combined from the following official guides:
* https://linkerd.io/2.11/tasks/generate-certificates/
* https://linkerd.io/2.11/tasks/install-helm/
* https://linkerd.io/2.11/tasks/installing-multicluster/
* https://linkerd.io/2.11/tasks/multicluster/

### Prerequisites
* Local installation of [Helm3](https://helm.sh/docs/intro/install/)
* Local installation of the [Linkerd CLI](https://linkerd.io/2.11/getting-started/) version 2.11
* A running Kubernetes cluster and access set up via kubectl in the local machine
* Local installation of openssl

## Linkerd and multicluster install

1. Create a trust anchor (root certificate) for signing certificates for each cluster. Ideally this would be done by a trusted third-party but choosing one of the computing parties would also suffice for that matter, as long it is kept from the public. This only plays a role in the mutual authentication and encryption of data in transit between clusters.
```
$ openssl ecparam -out ca.key -name prime256v1 -genkey -noout
$ openssl req -x509 -sha256 -new -nodes -key ca.key -days 365 -out ca.crt \
    -subj '/CN=root.linkerd.cluster.local' \
    -addext keyUsage=critical,cRLSign,keyCertSign \
    -addext basicConstraints=critical,CA:true,pathlen:1
```

2. Specify Linkerd's Helm repository
```
$ helm repo add linkerd https://helm.linkerd.io/stable
```

***The following steps have to be completed for each Kubernetes cluster***

3. Create a key and intermediate certificate for Linkerd. In the case where the CA is some external entity, the first and second commands could be done by a party in private, and the certificate signing request (`intermediate.csr`) sent to be signed to the party holding the trust root key (`ca.key`).
```
$ openssl ecparam -out intermediate.key -name prime256v1 -genkey -noout
$ openssl req -new -sha256 \                                                                      
    -key intermediate.key \
    -subj "/CN=identity.linkerd.cluster.local" \                                                  
    -out intermediate.csr
$ openssl x509 -req -in intermediate.csr -CA ca.crt -CAkey ca.key \
    -CAcreateserial -out intermediate.crt -days 365 -sha256 \
    -extensions v3_ca -extfile ./ca_extensions.conf
```

4. Install Linkerd using Helm. Take note that all clusters have to be configured with the same `ca.crt` file.
```
$ helm install linkerd2 \
    --set-file identityTrustAnchorsPEM=ca.crt \
    --set-file identity.issuer.tls.crtPEM=intermediate.crt \
    --set-file identity.issuer.tls.keyPEM=intermediate.key \
    linkerd/linkerd2
```

5. Install multicluster using Helm
```
$ helm install linkerd2-multicluster \
    --set gateway.replicas=3 \
    linkerd/linkerd2-multicluster
```

6. Use the Linkerd CLI to generate a link configuration. `$SHORT_NAME` is the dns-friendly name set in the [Sharemind helm chart readme](../sharemind/README.md) as `mirrorDesignation`, identifying the current cluster.
```
$ linkerd multicluster link \
    --set "enableHeadlessServices=true" \
    --cluster-name="$SHORT_NAME" > "$SHORT_NAME-link.yaml"
```

7. Wait until the components are ready
```
$ linkerd check
```

***The following step has to be completed for each pair of clusters***

8. For a three-party multicluster setup, all parties have to have sent both neighbours their `${SHORT_NAME}-link.yaml` files. Then they should apply each of the the link configurations to their cluster.
```
$ kubectl apply -n linkerd-multicluster -f "$NEIGHBOUR_SHORT_NAME-link.yaml"
```

9. The functioning of the inter-cluster communication can be verified using the following command.
```
$ linkerd multicluster check
```
If Sharemind servers are already running in the neighbouring clusters, then it also indicates whether the endpoints are successfully mirrored.
