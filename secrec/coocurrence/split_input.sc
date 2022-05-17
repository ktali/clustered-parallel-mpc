/*
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

import stdlib;
import shared3p;
import table_database;
import shared3p_table_database;

domain pd_shared3p shared3p;

// This program is required as currently concurrent access to HDF5 files is disallowed
// The goal is to split the input data preemptively to n tables, where n is the number
// of map tasks
void main() {
  string datasource = "DS1";
  string in_table_name = "input";
  string out_table_name_prefix = "input-";

  uint num_cliques = argument("num_cliques");

  tdbOpenConnection(datasource);

  uint row_count = tdbGetRowCount(datasource, in_table_name);
  uint slice_size = row_count / num_cliques;

  uint inputs_vmap_id = tdbReadColumn(datasource, in_table_name, "inputs");

  uint schema = tdbVmapNew();
  pd_shared3p uint32 vtype;
  tdbVmapAddVlenType(schema, "types", vtype);
  tdbVmapAddString(schema, "names", "inputs");

  for (uint clique_idx = 0;
       clique_idx < num_cliques;
       clique_idx++) {
    string clique_table_name = out_table_name_prefix + tostring(clique_idx);
    uint start_idx = slice_size * clique_idx;
    uint end_idx;
    if (clique_idx == num_cliques - 1)
      end_idx = row_count;
    else
      end_idx = start_idx + slice_size;

    if (tdbTableExists(datasource, clique_table_name))
      tdbTableDelete(datasource, clique_table_name);
    tdbTableCreate(datasource, clique_table_name, schema);

    uint params = tdbVmapNew();

    for (uint i = start_idx; i < end_idx; i++) {
      if (i > start_idx)
        tdbVmapAddBatch(params);
      pd_shared3p uint32[[1]] values = tdbVmapGetVlenValue(inputs_vmap_id, "values", i);
      tdbVmapAddVlenValue(params, "values", values);
    }
    tdbInsertRow(datasource, clique_table_name, params);
    tdbVmapDelete(params);
  
  }
  tdbVmapDelete(schema);
  tdbCloseConnection(datasource);
}
