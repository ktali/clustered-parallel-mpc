### Example of parallel MapReduce -- Term coocurrence with the "stripes-method"

#### _SecreC files_
* `upload_data.sc` is for uploading chunks of input vectors to the cluster
* `split_input.sc` takes the number of parallel `map` tasks that are expected to run and prepares the partitions of input data for them.
* `map.sc` computes the map function and emits the results to an intermediary table.
* `partition.sc` takes the emissions and prepares them for the `reduce` tasks, i.e. it splits the emissions into partitions such that each reduce instance receives all values of the same key
* `reduce.sc` aggregates the emitted key-value, grouping by the key

