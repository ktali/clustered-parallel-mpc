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
import shared3p_sort;
import stdlib;
import table_database;

import profiling;

domain pd_shared3p shared3p;


void main() {

  string datasource = "DS1";

  uint clique_idx = argument("clique_idx");

  uint num_cliques = argument("num_cliques");
  uint stripe_length = argument("stripe_length");

  string table_name = "input-$clique_idx";
  tdbOpenConnection(datasource);

  uint num_entries = tdbGetRowCount(datasource, table_name);

  uint32 sectionType = newSectionType("map-$clique_idx");
  uint32 section = startSection(sectionType, num_entries);

  uint32 section_readdb_type = newSectionType("map-read-$clique_idx");
  uint32 section_readdb = startSection(section_readdb_type, num_entries);

  uint inputs_vmap_id = tdbReadColumn(datasource, table_name, "inputs");

  endSection(section_readdb);

  string emit_table_name = "emit-" + tostring(clique_idx);
  uint params = tdbVmapNew();
  pd_shared3p uint32 vtype;
  tdbVmapAddType(params, "types", vtype);
  tdbVmapAddString(params, "names", "key");
  for (uint i = 0; i < stripe_length; i++) {
    tdbVmapAddType(params, "types", vtype);
    tdbVmapAddString(params, "names", "value_$i");
    tdbVmapAddType(params, "types", vtype);
    tdbVmapAddString(params, "names", "count_$i");
  } 

  if (tdbTableExists(datasource, emit_table_name))
    tdbTableDelete(datasource, emit_table_name);
  tdbTableCreate(datasource, emit_table_name, params);

  tdbVmapDelete(params);

  params = tdbVmapNew();

  pd_shared3p uint32 zero = 0;

  for (uint i = 0; i < num_entries; i++) {
    pd_shared3p uint32[[1]] row = tdbVmapGetVlenValue(inputs_vmap_id, "values", i);
    pd_shared3p uint32[[1]] sorted_row = quicksort(row);
    bool[[1]] uneq_to_prev = declassify(sorted_row != cat({0}, sorted_row[:size(sorted_row) - 1]));
    
    uint num_unique = count_bits(uneq_to_prev, {0::uint}, {size(uneq_to_prev)})[0];
    uint[[1]] starts (num_unique);
    uint starts_ptr = 0;
    for (uint j = 0; j < size(sorted_row); j++) {
      if (uneq_to_prev[j]) {
        starts[starts_ptr] = j;
        starts_ptr++;
      }
    }
    uint[[1]] ends = cat(starts[1:], {size(uneq_to_prev)});
    pd_shared3p uint32[[1]] counts = (uint32) (ends - starts);
    pd_shared3p uint32[[1]] unique_keys = partialRearrange(sorted_row, starts);
    for (uint j = 0; j < num_unique; j++) {
      if (i > 0 || j > 0)
        tdbVmapAddBatch(params);
      tdbVmapAddValue(params, "values", unique_keys[j]);
      uint num_added = 0;
      for (uint k = 0; k < num_unique; k++) {
        if (num_added >= stripe_length)
          break;
        if (j != k) {
          tdbVmapAddValue(params, "values", unique_keys[k]);
          tdbVmapAddValue(params, "values", counts[k]);
          num_added++;
        }
      }
      // Add zero padding
      while (num_added < stripe_length) {
        tdbVmapAddValue(params, "values", zero);
        tdbVmapAddValue(params, "values", zero);
        num_added++;
      }
    }
  }

  uint32 section_writedb_type = newSectionType("map-write-$clique_idx");
  uint32 section_writedb = startSection(section_writedb_type, tdbVmapGetBatchCount(params));

  tdbInsertRow(datasource, emit_table_name, params);

  endSection(section_writedb);

  tdbVmapDelete(params);
  tdbCloseConnection(datasource);
 
  endSection(section);
  flushProfileLog();

}
