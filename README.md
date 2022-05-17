# Parallel and Cloud-Native Secure Multi-Party Computation

This repository contains the source code of the master's thesis of Kert Tali.
The work is split as follows
* [Helm chart for deploying the Sharemind servers and gateway to the Kubernetes cluster](helm/sharemind) with [instructions on how to connect parties' clusters with Linkerd multi-cluster](helm/multicluster)
* [Parallel gateway application for running parallel MPC tasks on the cluster](gateway)
* [SecreC examples of parallel MPC programs](secrec)
* [Client applications that run said programs](client)

Details about each component are laid out in their respective `README.md` files.

## License

Files in this repository are copyright (c) Cybernetica AS
