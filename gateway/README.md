## Gateway application for parallel MPC programs

This directory contains the gateway application Java project.

### Compiling the container image for cluster deployment

#### Prerequisites for compiling

* A Java Archive (.jar) of the adapted JNI Sharemind Gateway library (named `jni-sharemind-gateway.jar`) in the root of this project. This is not included with this repository and can only be attained by request from the author or the provider of your Sharemind license.
* Credentials file for the Sharemind APT repository (version 2022.03) as described [here](https://docs.sharemind.cyber.ee/2022.03/installation) as file `sharemind.conf`.
* A private (Docker) registry for which system-wide credentials are in place


The Java code must first be packaged locally, using
```
$ ./gradlew install
```

A `Dockerfile` is provided to build the image based on the build artifacts with the following commands
```
$ cp Dockerfile sharemind.conf build/install/gateway/
$ cd build/install/gateway
$ export DOCKER_BUILDKIT=1
$ docker build --secret id=sharemind-apt,src=sharemind.conf . --tag <YOUR DOCKER REGISTRY>/sharemind-parallel-gateway:0.1
$ docker push <YOUR DOCKER REGISTRY>/sharemind-parallel-gateway:0.1
```

The gateway can now be pulled by the [Kubernetes Deployment of this repository](../helm/sharemind/), provided the image is specified in its [`values.yaml`](../helm/sharemind/values.yaml) file.

