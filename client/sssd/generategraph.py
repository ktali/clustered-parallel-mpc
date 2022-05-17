#!/usr/bin/python3

import random
import networkx as nx
import sys

outfile = sys.argv[1]
numnodes = sys.argv[2]
numedges = sys.argv[3]

G = nx.gnm_random_graph(numnodes, numedges, seed=123123, directed=True)
for u, v, w in G.edges(data=True):
    w["weight"] = random.randint(1, 128)
with open(outfile, "w") as f:
    for node in G.adj:
        f.write(f"{node}:")
        f.write(','.join([f"{x[0]}|{x[1]['weight']}" for x in G.adj[node].items()]))
        f.write("\n")

