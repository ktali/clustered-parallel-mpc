/*
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

import rearrange;
import shared3p;
import shared3p_table_database;
import shared3p_random;
import shared3p_sort;
import shared3p_permutation;
import stdlib;
import table_database;

import profiling;

domain pd_shared3p shared3p;

void main() {

  string datasource = "DS1";

  // The emitted key value paris of the map tasks are read in and concatenated
  string input_table_name_prefix = "emit-";

  // The concatenated map results will be sorted by their keys
  // and partitioned so that every emitted key-value pair occurs once
  string partition_table_name_prefix = "part-";

  // Amount of parallel map tasks ran before the partitioner
  uint num_map_cliques = argument("num_map_cliques");
  // Amount of parallel reduce tasks to be run after the partitioner
  uint num_reduce_cliques = argument("num_reduce_cliques");  

  // Concatenation of map results
  tdbOpenConnection(datasource);

  // the columns should be {key, value_0, count_0, ... value_n, count_n}, find n
  // uint stripe_length = (tdbGetColumnCount(datasource, input_table_name_prefix + tostring(0)) - 1) / 2;
  uint cols = tdbGetColumnCount(datasource, input_table_name_prefix + tostring(0));

  uint num_emissions = 0;
  uint[[1]] stop_indices (num_map_cliques + 1) = 0;
  for (uint i = 0; i < num_map_cliques; i++) {
    num_emissions += tdbGetRowCount(datasource, input_table_name_prefix + tostring(i));
    stop_indices[i + 1] = num_emissions;
  }

  uint32 sectionType = newSectionType("partition");
  uint32 section = startSection(sectionType, num_emissions);

  pd_shared3p uint32[[2]] m (num_emissions, cols);

  uint32 section_readdb_type = newSectionType("partition-read");
  uint32 section_readdb = startSection(section_readdb_type, num_emissions);

  for (uint i = 0; i < num_map_cliques; i++) {
    for (uint j = 0; j < cols; j++) {
      m[stop_indices[i]:stop_indices[i+1], j] =
        tdbReadColumn(datasource, input_table_name_prefix + tostring(i), j);
    }
  }

  endSection(section_readdb);

  // Sorting of map results by the private keys
  m = quicksort(m, 0::uint);

  // Mark the starts of every new key in the sorted array with `false`
  bool[[1]] keys_equal_to_previous = declassify(m[:, 0] == cat({m[0, 0]}, m[:num_emissions - 1, 0]));


  // Approximate size of each partition, may be smaller or larger --
  // determined by the starts of new keys
  uint entries_per_reducer = num_emissions / num_reduce_cliques;
  uint[[1]] starts (num_reduce_cliques);
  // Partition the data, find splitting indices
  for (uint i = 1; i < num_reduce_cliques; i++) {
    // First taking the starts assuming equal widths 
    uint start = entries_per_reducer * i;
    // Then we bump up the partition splits until it lines up with a new key
    while (keys_equal_to_previous[start]) {
      start++;
    }
    starts[i] = start; 
  }
  uint[[1]] ends = cat(starts[1:], {num_emissions});
  // TODO: split index finding may produce empty tables

  // Vmap for partition data table metadata
  uint params_data_meta = tdbVmapNew();
  pd_shared3p uint32 vtype;
  tdbVmapAddType(params_data_meta, "types", vtype);
  tdbVmapAddString(params_data_meta, "names", "key");
  for (uint i = 0; i < (cols - 1) / 2; i++) { // `(cols - 1) / 2` is equal to stripe length
    tdbVmapAddType(params_data_meta, "types", vtype);
    tdbVmapAddString(params_data_meta, "names", "value_$i");
    tdbVmapAddType(params_data_meta, "types", vtype);
    tdbVmapAddString(params_data_meta, "names", "count_$i");
  }

  uint32 section_writedb_type = newSectionType("partition-write");

  for (uint current_reducer = 0;
       current_reducer < num_reduce_cliques;
       current_reducer++) {

    string table_name = partition_table_name_prefix + tostring(current_reducer);
    if (tdbTableExists(datasource, table_name))
      tdbTableDelete(datasource, table_name);
    tdbTableCreate(datasource, table_name, params_data_meta);

    // Vmap for partition data
    uint params_data = tdbVmapNew();
    tdbVmapSetValueAsColumn(params_data);

    for (uint i = 0; i < cols; i++)
      tdbVmapAddValue(params_data, "values", m[starts[current_reducer]:ends[current_reducer], i]);

    uint32 section_writedb = startSection(section_writedb_type, ends[current_reducer] - starts[current_reducer]);
    
    tdbInsertRow(datasource, table_name, params_data);

    endSection(section_writedb);

    tdbVmapDelete(params_data);
  }
  tdbVmapDelete(params_data_meta);
  tdbCloseConnection(datasource);
 
  endSection(section);
  flushProfileLog();

}
