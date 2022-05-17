### Example of parallel BSP -- Single-source shortest path lengths

_SecreC files_
* `preupload.sc` is for uploading chunks of input vectors to the cluster
* `finalize_upload.sc` takes the number of parallel `computation` tasks that are expected to run and prepares the partitions of input data for them.
* `computation.sc` computes the map function and emits the results to an intermediary table. Publishes an abort message, if all the states of the vertices remain the same after the message processing. If all instances publish this message, the iterative algorithm is concluded.
* `communication.sc` takes the messages and prepares them for the following `computation` run, i.e. it splits the emissions into partitions such that each computation instance receives all the messages sent to its vertices

