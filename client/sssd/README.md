### Client application for running the parallel *Single Source Shortest Distances* algorithm

#### _Input generation and result verification_
Random input graphs can be generated by running 

```./generatedata.py <filename> <number of vertices> <number of edges>```
 
After running the computation, the secret shared databases named `graph-state-part-<CLIQUE IDX>.h5` can be combined using the `sharemind-hdf5-csv-exporter` utility program and the resulting CSV outputs checked against a cleartext solve of the problem as follows  

```./checksolution.py <input filename> <path of directory containing 'graph-state-part-i.csv'> <starting vertex id of sssd>```

#### Tasks

* `sharemind-web-client` has to be installed and its path set in `package.json` under the correct dependency.
* This input file containing term vectors first needs to be converted to JSON with `./inputtojson.py <path-to-input.txt> <starting vertex id of sssd> <nr-of-partitions>`
  * the second argument is the amount of partitions that the inputs will be split into. The partitions are uploaded separately and they have to be small enough to not exceed the socket argument size allowance (~<20 kB).
* Set the required constants in `js/globals.js`
  * `numParts` -- Number of JSON partitions that were created with `./inputtojson.py`
  * `gatewayHosts` -- IP addresses assigned to each clusters gateway service. (Listed under `EXTERNAL-IP` in the output of command `kubectl get svc test-sharemind-web-gateway`)
  * `numComputationCliques` -- The degree of parallelism of the computation stage

#### Running

```
$ npm install
$ node js/node_client.js
```
