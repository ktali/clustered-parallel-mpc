#!/usr/bin/python3

import random
import sys

filename = sys.argv[1]
num_vectors = int(sys.argv[2])
min_vector_length = int(sys.argv[3])
max_vector_length = int(sys.argv[4])

with open(filename, "w") as f:
  for i in range(num_vectors):
    f.write(",".join([str(random.randint(1,128)) for _ in range(random.randint(min_vector_length, max_vector_length))]) + "\n")
