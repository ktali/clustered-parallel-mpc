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
 
  uint num_nodes = argument("num_nodes");
  tdbOpenConnection(datasource);
  uint params_state = tdbVmapNew();
  uint params_graph = tdbVmapNew();
  
  for (uint i = 0; i < num_nodes; i++) {
    uint32 node_id = argument("nid_$i");
    pd_shared3p uint32[[1]] edges = argument("nid_$i\_edges");
    pd_shared3p uint32[[1]] weights = argument("nid_$i\_weights");
    pd_shared3p uint32 initial_state = argument("nid_$i\_state");
    assert(size(edges) == size(weights));
    if (i > 0) {
      tdbVmapAddBatch(params_state);
      tdbVmapAddBatch(params_graph);
    }
    tdbVmapAddValue(params_state, "values", node_id);
    // We select the initial node without revealing it by setting its initial state to 0
    tdbVmapAddValue(params_state, "values", initial_state);
    tdbVmapAddVlenValue(params_graph, "values", edges);
    tdbVmapAddVlenValue(params_graph, "values", weights);
  }
  tdbInsertRow(datasource, state_table_name, params_state);
  tdbInsertRow(datasource, graph_table_name, params_graph);
  tdbVmapDelete(params_state);
  tdbVmapDelete(params_graph);
  tdbCloseConnection(datasource);

}
