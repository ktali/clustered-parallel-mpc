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

void main() {
  string datasource = "DS1";
  string state_table_name = "graph-state-part-";
  string graph_table_name = "graph-part-";
  string full_state_table_name = "graph-state";
  string full_graph_table_name = "graph";
  string last_nodes_table_name = "clique-last-nodes";


  uint num_cliques = argument("num_cliques");

  tdbOpenConnection(datasource);

  // Clique last node table
  uint params = tdbVmapNew();
  {
    uint32 vtype;
    tdbVmapAddType(params, "types", vtype);
    tdbVmapAddString(params, "names", "node_id"); 
  }
  if (tdbTableExists(datasource, last_nodes_table_name))
    tdbTableDelete(datasource, last_nodes_table_name);
  tdbTableCreate(datasource, last_nodes_table_name, params);
  tdbVmapDelete(params); 

  // State partition tables
  params = tdbVmapNew();
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

  for (uint i = 0; i < num_cliques; i++) {
    if (tdbTableExists(datasource, state_table_name + tostring(i)))
      tdbTableDelete(datasource, state_table_name + tostring(i));
    tdbTableCreate(datasource, state_table_name + tostring(i), params);
  }
  tdbVmapDelete(params);

  // Graph structure partition tables
  params = tdbVmapNew();
  {
    pd_shared3p uint32 vtype;
    tdbVmapAddVlenType(params, "types", vtype);
    tdbVmapAddString(params, "names", "edges");
    tdbVmapAddVlenType(params, "types", vtype);
    tdbVmapAddString(params, "names", "weights");
  }

  for (uint i = 0; i < num_cliques; i++) {
    if (tdbTableExists(datasource, graph_table_name + tostring(i)))
      tdbTableDelete(datasource, graph_table_name + tostring(i));
    tdbTableCreate(datasource, graph_table_name + tostring(i), params);
  }
  tdbVmapDelete(params);

  uint params_state = tdbVmapNew();
  uint params_graph = tdbVmapNew();
  uint params_last_nodes = tdbVmapNew();

  uint num_nodes = tdbGetRowCount(datasource, full_state_table_name);

  uint32[[1]] node_ids = tdbReadColumn(datasource, full_state_table_name, "node_id");
  pd_shared3p uint32[[1]] state = tdbReadColumn(datasource, full_state_table_name, "state");
  // get edges vlen ptr
  uint edges_vmap_id = tdbReadColumn(datasource, full_graph_table_name, "edges");
  // get weights vlen ptr
  uint weights_vmap_id = tdbReadColumn(datasource, full_graph_table_name, "weights");


  uint slice_size = num_nodes / num_cliques;
  uint current_partition = 0;

  pd_shared3p uint32[[1]] empty (0);

  for (uint i = 0; i < num_nodes; i++) {
    uint32 node_id = node_ids[i];
    pd_shared3p uint32 initial_state = state[i];
    pd_shared3p uint32[[1]] edges = tdbVmapGetVlenValue(edges_vmap_id, "values", i);
    pd_shared3p uint32[[1]] weights = tdbVmapGetVlenValue(weights_vmap_id, "values", i);

    assert(size(edges) == size(weights));
    if (i % slice_size > 0 || num_cliques > 1 && current_partition + 1 == num_cliques) {
      tdbVmapAddBatch(params_state);
      tdbVmapAddBatch(params_graph);
    } else if (i % slice_size == 0 && i > 0) {
      tdbInsertRow(datasource, state_table_name + tostring(current_partition), params_state);
      tdbInsertRow(datasource, graph_table_name + tostring(current_partition), params_graph);
      tdbVmapAddValue(params_last_nodes, "values", node_ids[i - 1]);
      tdbInsertRow(datasource, last_nodes_table_name, params_last_nodes);
      tdbVmapDelete(params_last_nodes);
      params_last_nodes = tdbVmapNew();
      tdbVmapDelete(params_state);
      tdbVmapDelete(params_graph);
      params_state = tdbVmapNew();
      params_graph = tdbVmapNew();
      current_partition++;
    }
    tdbVmapAddValue(params_state, "values", node_id);
    // We select the initial node without revealing it by setting its initial state to 0
    tdbVmapAddValue(params_state, "values", initial_state);
    tdbVmapAddVlenValue(params_graph, "values", edges);
    tdbVmapAddVlenValue(params_graph, "values", weights);
  }
  tdbInsertRow(datasource, state_table_name + tostring(current_partition), params_state);
  tdbInsertRow(datasource, graph_table_name + tostring(current_partition), params_graph);
  tdbVmapDelete(params_state);
  tdbVmapDelete(params_graph);
  tdbVmapAddValue(params_last_nodes, "values", node_ids[size(node_ids) - 1]);
  tdbInsertRow(datasource, last_nodes_table_name, params_last_nodes);
  tdbVmapDelete(params_last_nodes);
  tdbCloseConnection(datasource);
}
