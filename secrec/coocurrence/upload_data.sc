/*
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

import table_database;
import shared3p_table_database;
import shared3p;
import stdlib;

domain pd_shared3p shared3p;

void main() {
  string datasource = "DS1";
  string table_name = "input";

  uint num_entries = argument("num_entries");
  bool overwrite = argument("overwrite");

  tdbOpenConnection(datasource);
  
  if (overwrite && tdbTableExists(datasource, table_name))
      tdbTableDelete(datasource, table_name);

  if (!tdbTableExists(datasource, table_name)) {
    uint params = tdbVmapNew();
    pd_shared3p uint32 vtype;
    tdbVmapAddVlenType(params, "types", vtype);
    tdbVmapAddString(params, "names", "inputs");
    tdbTableCreate(datasource, table_name, params);
    tdbVmapDelete(params);
  } 

  uint params = tdbVmapNew();
  for (uint i = 0; i < num_entries; i++) {
    pd_shared3p uint32[[1]] values = argument("v_$i");
    if (i > 0)
      tdbVmapAddBatch(params);
    tdbVmapAddVlenValue(params, "values", values);
  }

  tdbInsertRow(datasource, table_name, params);
  tdbVmapDelete(params);
   
  tdbCloseConnection(datasource);
}
