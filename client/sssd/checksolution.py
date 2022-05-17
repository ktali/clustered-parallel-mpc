#!/usr/bin/python3

import networkx as nx
import copy
import sys
import os

graphfile = sys.argv[1]
out_csvs_path = sys.argv[2]
sssd_source = int(sys.argv[3])

graph = {}
with open(graphfile, "r") as f:
  for l in f:
    v, e = l.split(":")
    if len(e.strip()) > 0:
      graph[int(v)] = [(int(x.split("|")[0]), int(x.split("|")[1])) for x in e.split(",")]
    else:
      graph[int(v)] = []
G = nx.DiGraph()
for v in graph.keys():
  G.add_node(v)
for v, e in graph.items():
  for t in e:
    G.add_edge(v, t[0], weight=t[1])

length = nx.single_source_dijkstra_path_length(G, sssd_source)

for i in range(5):
  with open(os.path.join(out_csvs_path, f"graph-state-part-{i}.csv"), "r") as f:
    f.readline()
    for l in f:
      k, v = [int(x) for x in l.strip().split(',')]
      if k not in length:
        assert v > 2147483646
        print(f"No path to vertex {k}")
      elif length[k] != v:
        print(f"Path to vertex {k} not shortest! {length[k]=} != {v=}")
      else:
        print(f"Path to vertex {k} OK!")
