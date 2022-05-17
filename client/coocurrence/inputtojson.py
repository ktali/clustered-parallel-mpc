#!/usr/bin/python3
import sys
import json

filename = sys.argv[1]
numpartitions = int(sys.argv[2])

inputs = []
with open(filename, "r") as f:
  for l in f:
    inputs.append([int(x) for x in l.split(",")])

outs = []
partsize = len(inputs) // numpartitions

for i in range(numpartitions):
  with open(f"input-{i}.json", "w") as f:
    json.dump(inputs[partsize * i : (partsize * (i + 1) if i < numpartitions - 1 else len(inputs))], f, indent=2)
