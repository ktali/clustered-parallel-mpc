#!/usr/bin/python3

import itertools
import sys
import os

input_file_path = sys.argv[1]
output_file_path_prefix = sys.argv[2]

inputs = []
outputs = []
with open(input_file_path, "r") as f:
  for l in f:
    inputs.append([int(x) for x in l.split(",")])
coocurrences = {}
for i in inputs:
  for j in set(i):
    for k in set(i):
      if j != k:
        if (j, k) not in coocurrences:
          coocurrences[(j, k)] = 0
        coocurrences[(j, k)] += i.count(k)

for i in range(3):
  with open(os.path.join(output_file_path_prefix, f"out-{i}.csv"), "r") as f:
    f.readline()
    for l in f:
      k1, k2, c = [int(x) for x in l.strip().split(',')]
      if k2 != 0:
        if coocurrences[(k1, k2)] != c:
          print(f"Clique {i}: expected ({k1},{k2}) = {coocurrences[(k1, k2)]}, got {c}.")
        else:
          print(f"Clique {i}: OK ({k1},{k2}) = {coocurrences[(k1, k2)]}")
