/*
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

import stdlib;
import table_database;
import shared3p_table_database;
import shared3p;

domain pd_shared3p shared3p;

void main() {

  string datasource = "DS1";
  string state_table_name = "graph-state";
  string graph_table_name = "graph";
 
  tdbOpenConnection(datasource);
  
  // State tables
  uint params = tdbVmapNew();
  {
    uint32 vtype;
    tdbVmapAddType(params, "types", vtype);
    tdbVmapAddString(params, "names", "node_id");
  }
  {
    pd_shared3p uint32 vtype;
    tdbVmapAddType(params, "types", vtype);
    tdbVmapAddString(params, "names", "state");
  }
  
  if (tdbTableExists(datasource, state_table_name))
    tdbTableDelete(datasource, state_table_name);
  tdbTableCreate(datasource, state_table_name, params);
  tdbVmapDelete(params);
  
  // Graph structure tables
  params = tdbVmapNew();
  {
    pd_shared3p uint32 vtype;
    tdbVmapAddVlenType(params, "types", vtype);
    tdbVmapAddString(params, "names", "edges");
    tdbVmapAddVlenType(params, "types", vtype);
    tdbVmapAddString(params, "names", "weights");
  }
  
  if (tdbTableExists(datasource, graph_table_name))
    tdbTableDelete(datasource, graph_table_name);
  tdbTableCreate(datasource, graph_table_name, params);
  tdbVmapDelete(params);

  tdbCloseConnection(datasource);
}
