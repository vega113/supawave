# Memento MCP Tool (Knowledge Graph Memory)

This document explains the Memento MCP (Model Context Protocol) memory tool that is available in this environment. It provides a graph-based, long‑term memory you can programmatically read and write via tool calls. Use it to persist entities (nodes), their relationships (edges), and observations (facts/notes) with optional metadata like strength, confidence, and time validity.

The Memento tool comes in two flavors:
- mcp_memento_*: Advanced knowledge graph memory with strength/confidence, versioning and time semantics.
- mcp_memory_*: A simpler API without enhanced properties. Prefer the mcp_memento_* API for new work.


## When to use it
- Persist user/team knowledge, decisions, and context between sessions.
- Model domain objects (e.g., Projects, Services, Endpoints) and how they relate.
- Track hypotheses and their confidence, and update confidence over time.
- Capture time-bounded facts (valid from/to) and versioned history.


## Core data model
- Entity (node): Has a name and type, plus a list of observations (arbitrary strings). Advanced API may include IDs, versions, timestamps.
- Relation (edge): Connects two entities with a relationType string, plus optional strength/confidence and time/version metadata.
- Observation: A discrete note/fact stored on an entity; can carry its own strength/confidence in the advanced API.


## Quickstart examples (conceptual)
Below are example payloads that match the tool interfaces. In an interactive session you would call these tools via the MCP function hooks rather than embedding in code.

Create entities:
```json
{
  "entities": [
    {
      "name": "Payments Service",
      "entityType": "Service",
      "observations": [
        "Owns /payments/* APIs",
        "Written in Kotlin"
      ]
    },
    {
      "name": "Orders DB",
      "entityType": "Database",
      "observations": [
        "PostgreSQL 14",
        "Primary region: us-east-1"
      ]
    }
  ]
}
```

Create a relation with confidence:
```json
{
  "relations": [
    {
      "from": "Payments Service",
      "to": "Orders DB",
      "relationType": "reads-from",
      "strength": 0.9,
      "confidence": 0.8
    }
  ]
}
```

Search nodes:
```json
{
  "query": "payments postgres"
}
```

Semantic search:
```json
{
  "query": "Which service reads the orders database?",
  "limit": 5,
  "min_similarity": 0.6,
  "entity_types": ["Service"],
  "hybrid_search": true,
  "semantic_weight": 0.7
}
```


## API Reference (Advanced: mcp_memento_*)

- mcp_memento_create_entities
  - entities: Array of { name, entityType, observations[], optional id/version/timestamps/changedBy }
  - Use to batch-create nodes with initial observations.

- mcp_memento_create_relations
  - relations: Array of { from, to, relationType, optional strength, confidence, id, version, timestamps, validFrom, validTo, changedBy }
  - Use to create edges between existing entities; accepts metadata.

- mcp_memento_add_observations
  - observations: Array of { entityName, contents[], optional strength, confidence }
  - strength/confidence can be set per observation; defaults can be provided at the top level.

- mcp_memento_delete_entities
  - entityNames: string[]
  - Removes entities and their relations.

- mcp_memento_delete_observations
  - deletions: Array of { entityName, observations[] }
  - Deletes specific observations from entities.

- mcp_memento_delete_relations
  - relations: Array of { from, to, relationType }
  - Removes the specified edges.

- mcp_memento_get_relation
  - { from, to, relationType }
  - Retrieves a single relation with enhanced properties.

- mcp_memento_update_relation
  - relation: { from, to, relationType, optional strength, confidence, id, version, timestamps, validFrom, validTo, changedBy }
  - Updates an existing relation’s properties.

- mcp_memento_read_graph
  - Returns the entire memory graph (use with care for large graphs).

- mcp_memento_search_nodes
  - { query }
  - Keyword-style search over entity names, types, and observations.

- mcp_memento_open_nodes
  - { names: string[] }
  - Fetch details for specific entities by name.

- mcp_memento_semantic_search
  - { query, limit, min_similarity, entity_types[], hybrid_search, semantic_weight }
  - Vector/semantic search with optional hybrid keyword+semantic ranking.

- mcp_memento_get_entity_embedding
  - { entity_name }
  - Returns the vector embedding for an entity.

- mcp_memento_get_entity_history
  - { entityName }
  - Version history for an entity.

- mcp_memento_get_relation_history
  - { from, to, relationType }
  - Version history for a relation.

- mcp_memento_get_graph_at_time
  - { timestamp }
  - Snapshot of the graph at a point in time.

- mcp_memento_get_decayed_graph
  - { reference_time?, decay_factor? }
  - Returns the graph with time-decayed confidence values.

- mcp_memento_force_generate_embedding
  - { entity_name }
  - Forces embedding computation for an entity.

- mcp_memento_debug_embedding_config
  - Debug tool for embedding configuration/status.

- mcp_memento_diagnose_vector_search
  - Diagnostic tool for vector search setup.


## API Reference (Basic: mcp_memory_*)
These mirror a subset of the advanced features, without strength/confidence/time/version fields.

- mcp_memory_create_entities: Create nodes.
- mcp_memory_create_relations: Create edges.
- mcp_memory_add_observations: Add observations to nodes.
- mcp_memory_delete_entities: Remove entities (and associated edges).
- mcp_memory_delete_observations: Remove specific observations.
- mcp_memory_delete_relations: Remove edges.
- mcp_memory_read_graph: Return entire graph.
- mcp_memory_search_nodes: Keyword search across graph.
- mcp_memory_open_nodes: Get specific entities by name.


## Naming and modeling best practices
- Use consistent, human‑readable names and a stable entityType taxonomy (e.g., Service, Database, API, Team).
- Keep observations short and atomic; prefer multiple observations over one long paragraph.
- Use relations in active voice: service A reads-from database B.
- Leverage strength (0.0–1.0) to indicate intensity/importance; confidence (0.0–1.0) to indicate belief in accuracy.
- Set validFrom/validTo for time-bounded facts (e.g., temporary ownership or incidents).
- Prefer idempotent writes: check for existing entities/relations to avoid duplicates.


## Privacy and safety
- Do not store secrets, credentials, or PII unless you have explicit approval and controls.
- Treat observations as potentially user-visible and exportable.
- Consider retention policies and use delete APIs to remove stale/sensitive data.


## Troubleshooting
- Duplicates: Normalize names (case, spacing) and check before creating.
- Missing search hits: Try both keyword search (search_nodes) and semantic_search with hybrid_search=true.
- Large graphs: Prefer targeted open_nodes queries and scoped searches over read_graph.
- Embeddings not working: Use debug_embedding_config and diagnose_vector_search tools.


## Notes
- The exact storage backend and capacity limits depend on the host environment.
- Tool calls are batched; where possible, group related creates/deletes for efficiency.
- If you need to snapshot or time-travel queries, prefer get_graph_at_time or get_decayed_graph.
