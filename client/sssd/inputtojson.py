#!/usr/bin/python3

import sys
import json

graphfile = sys.argv[1]
sssp_start = int(sys.argv[2])
numpartitions = int(sys.argv[3])

graph = {}
with open(graphfile, "r") as f:
  for l in f:
    v, e = l.split(":")
    if len(e.strip()) > 0:
      graph[int(v)] = [(int(x.split("|")[0]), int(x.split("|")[1])) for x in e.split(",")]
    else:
      graph[int(v)] = []

outs = [{}]
numnodes = len(graph)
partsize = numnodes // numpartitions
idx = 0
part = 0
for k in graph.keys():
  if idx >= partsize and len(outs) < numpartitions:
    part += 1
    outs.append({})
    idx = 0
  outs[part][f"nid_{idx}"] = k
  outs[part][f"nid_{idx}_edges"] = [x[0] for x in graph[k]]
  outs[part][f"nid_{idx}_weights"] = [x[1] for x in graph[k]]
  outs[part][f"nid_{idx}_state"] = 0 if k == sssp_start else 2147483647 
  idx += 1
for i in range(numpartitions):
  with open(f"input-{i}.json", "w") as f:
    json.dump(outs[i], f, indent=2)
