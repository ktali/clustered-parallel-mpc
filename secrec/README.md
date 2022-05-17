# MPC parallel programming

SecreC programs following MapReduce and bulk-synchronous parallel programming models.

The MapReduce example -- [Term co-ocurrence](coocurrence)  
The BSP example -- [Single-source shortest distances](sssd)

## Compiling

_inside each of the example directories_
```
$ mkdir bins
$ scc --input <program>.sc --output bins/<program>.sb --include ../lib/
```

## Uploading to the cluster

A kustomize script (`kustomize.yaml`) is provided to upload the binaries to the clusters.
Refer to the [Sharemind Helm chart documentation](../helm/sharemind/README.md) for usage.

## Running on the cluster

If clusters contain the necessary binaries and the inputs for the tasks are generated, then refer to the [client application documentation](../client/README.md) for usage.
