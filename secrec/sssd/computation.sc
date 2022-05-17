import stdlib;
import shared3p;

import table_database;
import shared3p_table_database;

import profiling;

domain pd_shared3p shared3p;

void main() {

  string datasource = "DS1";

  uint clique_idx = argument("clique_idx");

  uint32 sectionType = newSectionType("computation-$clique_idx");
  uint32 section = startSection(sectionType, 0::uint);

  bool is_first_iteration = argument("first_iteration");

  uint32 pseudo_inf = UINT32_MAX / 2;

  string state_table_name = "graph-state-part-$clique_idx";
  string graph_table_name = "graph-part-$clique_idx";
  string messages_table_name = "in-messages-$clique_idx";
  tdbOpenConnection(datasource);

  uint num_nodes = tdbGetRowCount(datasource, state_table_name);

  uint32 section_readdb_type = newSectionType("computation-read-$clique_idx");
  uint32 section_readdb = startSection(section_readdb_type, num_nodes);

  uint32[[1]] node_ids = tdbReadColumn(datasource, state_table_name, "node_id");
  pd_shared3p uint32[[1]] state = tdbReadColumn(datasource, state_table_name, "state");
  // get edges vlen ptr
  uint edges_vmap_id = tdbReadColumn(datasource, graph_table_name, "edges");
  // get weights vlen ptr
  uint weights_vmap_id = tdbReadColumn(datasource, graph_table_name, "weights");

  endSection(section_readdb);
  // --- Previous iteration message handling ---

  if (!is_first_iteration) {

    uint num_node_messages = tdbGetRowCount(datasource, messages_table_name);
    uint32[[1]] message_node_ids = tdbReadColumn(datasource, messages_table_name, "node_id");
    uint messages_vmap_id = tdbReadColumn(datasource, messages_table_name, "messages");
    pd_shared3p uint32[[1]] aggregated_incoming_messages (num_node_messages);

    pd_shared3p uint32[[1]] empty (0);

    for (uint i = 0; i < num_node_messages; i++) {
      pd_shared3p uint32[[1]] node_incoming_messages = tdbVmapGetVlenValue(messages_vmap_id, "values", i);
      aggregated_incoming_messages[i] = min(node_incoming_messages);
    }

    // join the aggregated messages to the whole set of nodes, filling in gaps with pseudo-infinity
    pd_shared3p uint32[[1]] aggregated_incoming_messages_filled (num_nodes) = pseudo_inf;
    uint message_ptr = 0;
    for (uint i = 0; i < num_nodes && message_ptr < num_node_messages; i++) {
      if (node_ids[i] == message_node_ids[message_ptr]) {
        aggregated_incoming_messages_filled[i] = aggregated_incoming_messages[message_ptr];
        message_ptr++;
      }
    }

    // Change state based on aggregated incoming messages, (SIMD)
    pd_shared3p uint32[[1]] modified_state = min(state, aggregated_incoming_messages_filled);
    if (declassify(all(modified_state == state))) {
      publish("abort-$clique_idx", 0);
    }
    state = modified_state;
  }

  // --- Message emission setup ---

  print("Message emission setup"); 
  string emit_table_name = "messages-$clique_idx";
  uint params = tdbVmapNew();
  {
    pd_shared3p uint32 vtype;
    tdbVmapAddType(params, "types", vtype);
    tdbVmapAddString(params, "names", "node_id");
    tdbVmapAddType(params, "types", vtype);
    tdbVmapAddString(params, "names", "message");
  }


  if (tdbTableExists(datasource, emit_table_name))
    tdbTableDelete(datasource, emit_table_name);
  tdbTableCreate(datasource, emit_table_name, params);
  tdbVmapDelete(params);
  params = tdbVmapNew();
  
  tdbVmapSetValueAsColumn(params);
  // --- Emitting messages for all neighbours ---
  bool skipbatch = true;
  print("iterating over $num_nodes\ nodes");
  for (uint i = 0; i < num_nodes; i++) {
    uint32 node_id = node_ids[i];
    pd_shared3p uint32[[1]] neighbours = tdbVmapGetVlenValue(edges_vmap_id, "values", i);
    pd_shared3p uint32[[1]] weights = tdbVmapGetVlenValue(weights_vmap_id, "values", i);
    // For adding one by one
    if (size(neighbours) > 0) {
      for (uint j = 0; j < size(neighbours); j++) { 
        if (!skipbatch)
          tdbVmapAddBatch(params);
        tdbVmapAddValue(params, "values", neighbours[j]);
        tdbVmapAddValue(params, "values", state[i] + weights[j]);
        skipbatch = false;
      }
    }
  }
  print("finished message emission");

  uint32 section_writedb_emission_type = newSectionType("computation-write-emission-$clique_idx");
  uint32 section_writedb_emission = startSection(section_writedb_emission_type, tdbVmapGetBatchCount(params));

  tdbInsertRow(datasource, emit_table_name, params);

  endSection(section_writedb_emission);

  tdbVmapDelete(params);

  // --- Saving new state ---

  tdbTableDelete(datasource, state_table_name);
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
  tdbTableCreate(datasource, state_table_name, params);
  
  tdbVmapSetValueAsColumn(params);
  tdbVmapAddValue(params, "values", node_ids);
  tdbVmapAddValue(params, "values", state);

  uint32 section_writedb_state_type = newSectionType("map-write-state-$clique_idx");
  uint32 section_writedb_state = startSection(section_writedb_state_type, tdbVmapGetBatchCount(params));

  tdbInsertRow(datasource, state_table_name, params);

  endSection(section_writedb_state);

  tdbVmapDelete(params);

  endSection(section);

  print("end of computation");
}
