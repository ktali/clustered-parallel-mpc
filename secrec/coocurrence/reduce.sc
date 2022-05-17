/*
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

import shared3p;
import shared3p_table_database;
import shared3p_sort;
import stdlib;
import table_database;
import profiling;

domain pd_shared3p shared3p;

template <type T, domain D : shared3p>
uint[[1]] starts_ends(D T[[1]] sorted_arr) {
  uint n = size(sorted_arr);
  bool[[1]] uneq_to_prev = declassify(sorted_arr != cat({sorted_arr[0] - 1}, sorted_arr[:n - 1]));
  uint num_keys = sum((uint) uneq_to_prev);
  uint[[1]] starts (num_keys);
  uint starts_ptr = 0;
  for (uint i = 0; i < n; i++) {
    if (uneq_to_prev[i]) {
      starts[starts_ptr] = i;
      starts_ptr++;
    }
  }
  uint[[1]] ends = cat(starts[1:], {size(uneq_to_prev)});
  return cat(starts, ends);
}

void main() {
  uint clique_idx = argument("clique_idx");

  string datasource = "DS1";
  string partition_table_name = "part-$clique_idx";
  string output_table_name = "out-$clique_idx";

  tdbOpenConnection(datasource);
  
  uint cols = tdbGetColumnCount(datasource, partition_table_name);
  uint rows = tdbGetRowCount(datasource, partition_table_name);

  uint32 sectionType = newSectionType("reduce-$clique_idx");
  uint32 section = startSection(sectionType, rows);

  uint stripe_length = (cols - 1) / 2;

  pd_shared3p uint32[[2]] m (rows, cols);


  uint32 section_readdb_type = newSectionType("reduce-read-$clique_idx");
  uint32 section_readdb = startSection(section_readdb_type, rows * cols);

  for (uint i = 0; i < cols; i++)
    m[:, i] = tdbReadColumn(datasource, partition_table_name, i);

  endSection(section_readdb);

  uint[[1]] ranges = starts_ends(m[:, 0]);
  uint num_keys = size(ranges) / 2;
  uint[[1]] starts = ranges[:num_keys];
  uint[[1]] ends = ranges[num_keys:];

  uint params = tdbVmapNew();
  pd_shared3p uint32 vtype;
  tdbVmapAddType(params, "types", vtype);
  tdbVmapAddString(params, "names", "left");
  tdbVmapAddType(params, "types", vtype);
  tdbVmapAddString(params, "names", "right");
  tdbVmapAddType(params, "types", vtype);
  tdbVmapAddString(params, "names", "count");

  if (tdbTableExists(datasource, output_table_name))
    tdbTableDelete(datasource, output_table_name);
 
  tdbTableCreate(datasource, output_table_name, params);
  tdbVmapDelete(params);

  params = tdbVmapNew();

  for (uint i = 0; i < num_keys; i++) {
    pd_shared3p uint32 key = m[starts[i], 0];
    pd_shared3p uint32[[2]] values_counts = reshape(m[starts[i]:ends[i], 1:], stripe_length * (ends[i] - starts[i]), 2);
    values_counts = quicksort(values_counts, 0::uint);
    uint[[1]] ranges_i = starts_ends(values_counts[:,0]);
    uint num_keys_i = size(ranges_i) / 2;
    uint[[1]] starts_i = ranges_i[:num_keys_i];
    uint[[1]] ends_i = ranges_i[num_keys_i:];
    for (uint j = 0; j < num_keys_i; j++) {
      if (i > 0 || j > 0)
        tdbVmapAddBatch(params);
      tdbVmapAddValue(params, "values", m[starts[i], 0]);
      tdbVmapAddValue(params, "values", values_counts[starts_i[j], 0]);
      tdbVmapAddValue(params, "values", sum(values_counts[starts_i[j]:ends_i[j], 1]));
    }
  }


  uint32 section_writedb_type = newSectionType("reduce-write-$clique_idx");
  uint32 section_writedb = startSection(section_writedb_type, tdbVmapGetBatchCount(params));

  tdbInsertRow(datasource, output_table_name, params);

  endSection(section_writedb);

  tdbVmapDelete(params);
  tdbCloseConnection(datasource);

  endSection(section);
  flushProfileLog();

}
