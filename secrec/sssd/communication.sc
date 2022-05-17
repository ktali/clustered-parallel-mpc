/*
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

import shared3p;
import shared3p_table_database;
import shared3p_random;
import shared3p_sort;
import shared3p_permutation;
import shared3p_keydb;
import stdlib;
import keydb;
import table_database;
import profiling;

domain pd_shared3p shared3p;

void main() {

  string datasource = "DS1";

  // Amount of parallel supersteps
  uint num_cliques = argument("num_cliques");

  uint32 sectionType = newSectionType("communication");
  uint32 section = startSection(sectionType, num_cliques);

  // Concatenation of map results
  tdbOpenConnection(datasource);

  // uint num_messages = tdbGetRowCount(datasource, "messages-0");
  uint num_messages = 0;
  uint[[1]] stop_indices (num_cliques + 1) = 0;
  for (uint i = 0; i < num_cliques; i++) {
    num_messages += tdbGetRowCount(datasource, "messages-$i");
    stop_indices[i + 1] = num_messages;
  }
  pd_shared3p uint32[[1]] node_ids (num_messages);
  pd_shared3p uint32[[1]] messages (num_messages);

  uint32 section_readdb_type = newSectionType("communication-read");
  uint32 section_readdb = startSection(section_readdb_type, num_messages);

  for (uint i = 0; i < num_cliques; i++) {
    node_ids[stop_indices[i]:stop_indices[i+1]] =
      tdbReadColumn(datasource, "messages-$i", "node_id");;
    messages[stop_indices[i]:stop_indices[i+1]] =
      tdbReadColumn(datasource, "messages-$i", "message");
  }
  uint num_entries = size(node_ids);
 
  // Generate this somehow?
  pd_shared3p uint8[[1]] shuffle_key(32); // the key and permutation could be persisted and reused if the order of messages is always the same.
  node_ids = shuffle(node_ids, shuffle_key);
  messages = shuffle(messages, shuffle_key);

  // Sorting of map results by the private keys
  pd_shared3p xor_uint64[[1]] indices = (xor_uint64) iota(num_entries);
  uint[[1]] perm = unsafeSort(node_ids, indices, true);  // IDEA: sort keys in map stage and merge them instead of concatting
  node_ids = applyPublicPermutation(node_ids, perm);
  messages = applyPublicPermutation(messages, perm);
  uint32[[1]] declassified_node_ids = declassify(node_ids);
  
  uint32[[1]] clique_last_node_ids = tdbReadColumn(datasource, "clique-last-nodes", "node_id");
  uint params_meta = tdbVmapNew();
  {
    uint32 vtype;
    tdbVmapAddType(params_meta, "types", vtype);
    tdbVmapAddString(params_meta, "names", "node_id");
  }
  {
    pd_shared3p uint32 vtype;
    tdbVmapAddVlenType(params_meta, "types", vtype);
    tdbVmapAddString(params_meta, "names", "messages");
  }

  uint node_id_start_ptr = 0;
  uint entry_ptr = 0;

  uint32 section_writedb_type = newSectionType("communication-write-clique-messages");
  
  for (uint clique_idx = 0;
       clique_idx < num_cliques;
       clique_idx++) {
    string sorted_messages_table_name = "in-messages-$clique_idx";
    if (tdbTableExists(datasource, sorted_messages_table_name))
      tdbTableDelete(datasource, sorted_messages_table_name);
    tdbTableCreate(datasource, sorted_messages_table_name, params_meta);

    uint params = tdbVmapNew();
    do {
      entry_ptr++;
      if (entry_ptr == num_entries
          || node_id_start_ptr != entry_ptr
             && declassified_node_ids[entry_ptr] != declassified_node_ids[entry_ptr - 1]) {
        tdbVmapAddValue(params, "values", declassified_node_ids[node_id_start_ptr]);
        tdbVmapAddVlenValue(params, "values", messages[node_id_start_ptr:entry_ptr]);
        node_id_start_ptr = entry_ptr;
        if (entry_ptr == num_entries 
            || declassified_node_ids[entry_ptr + 1] > clique_last_node_ids[clique_idx])
          break;
        else
          tdbVmapAddBatch(params);
      }
    } while (entry_ptr < num_entries); // Do check if there are any messages at all

    uint32 section_writedb = startSection(section_writedb_type, tdbVmapGetBatchCount(params));
    tdbInsertRow(datasource, sorted_messages_table_name, params);
    endSection(section_writedb);

    tdbVmapDelete(params);
  }
  tdbVmapDelete(params_meta);
}
