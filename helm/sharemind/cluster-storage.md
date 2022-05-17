## Notes about ReadWriteMany PersistentVolumes

Storage interfaces that multiple nodes can concurrently use for reading and writing are necessary for the thesis' proposed parallelism to communicate values between one Sharemind servers, which are potentially on different physical nodes.
This chart uses a PersistentVolumeClaim to ask the Kubernetes Control Plane for one such volume and mount it to each of the server Pods.
However, a StorageClass has to be defined for this to succeed -- a StorageClass defines what the storage backing the volume actually is and how to reach it.

Here are some tips noted from during the testing this chart on various types of clusters.

### Bare-metal clusters and cluster test environments (k3s, KinD, etc)

The best method in test environments would be to use an already provisioned Network Filesystem (NFS) server and use the [nfs-subdir-external-provisioner](https://github.com/kubernetes-sigs/nfs-subdir-external-provisioner) courtesy of the Kubernetes Special Interest Group. If deployed as shown in the guide under the link, this charts `values.yaml` should include the field

```hdf5StorageClass: nfs-client```

In production scenarios, a more performant backing storage should be used.
A possible solution would be to use a distributed storage solution that provisions volumes on the cluster nodes.

One such is the [Rook Ceph project](https://rook.io/). The specific mode of operation is [*Shared Filesystem*](https://rook.io/docs/rook/v1.9/ceph-filesystem.html). If deployed according to the guide, modify `values.yaml` as follows

```hdf5StorageClass: rook-cephfs```

### Google Kubernetes Engine

In GCPs portfolio of cloud services, there is the Filestore along with a supported container-storage interface (CSI) driver for Kubernetes that supports ReadWriteMany NFS shares as PersistentVolumes.

To set this up obtain a Filestore instance from GCP and follow [the guide](https://cloud.google.com/filestore/docs/accessing-fileshares) to configure Kubernetes to use it.

Finally, this chart should be installed with the StorageClass set to an empty string:

```hdf5StorageClass: "\"\""```

GKE allows for provisioning extra local SSDs with the cluster node pools, this means that an alternative would be to use Rook Ceph.

### Elastic Kubernetes Service (EKS)

Amazon's EFS can be used similarly to attach NFS file shares on. EBS can not be used as it does not support concurrent writing.

The setup is more involved, as described [here](https://aws.amazon.com/premiumsupport/knowledge-center/eks-persistent-storage/#Option_B.3A_Deploy_and_test_the_Amazon_EFS_CSI_driver), but is cheaper than Google Filestore.

Upon setup, the StorageClass needs to be:

```hdf5StorageClass: efs-sc```

NVMe devices can be attached to EKS nodes, meaning that using Rook Ceph is also possible.

### Azure Kubernetes Service

AKS manages dynamic PersistentVolume provisioning with Azure Files. Installing and using the corresponding CSI driver is described in [this how-to guide](https://docs.microsoft.com/en-us/azure/aks/azure-files-csi#dynamically-create-azure-files-pvs-by-using-the-built-in-storage-classes).

If following the dynamic provisioning guide, one of the following StorageClasses should be used:

```hdf5StorageClass: azurefile-csi-premium``` -- for SSD storage

or

```hdf5StorageClass: azurefile-csi``` -- for HDD storage