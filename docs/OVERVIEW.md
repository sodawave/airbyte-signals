# Puente on Airbyte

Puente is a native Airbyte extension that replicates broker fills from a configured source to market orders on a configured destination. It does not broker, advise, custody funds, or run a parallel application.

The product is built on Airbyte's last complete public OSS platform revision, `feadfd4ebf42150a01ee5dc4b611db0175e1513e`. Native Airbyte modules remain authoritative:

- connector contracts and protocol for broker actors;
- source and destination actor configuration for entity symbol maps;
- `StandardSync` and its persistence for saved connection crosswalk policy;
- replication workers for deterministic pre-write policy;
- server OpenAPI and repositories for journal and quota access;
- `airbyte-webapp` for every user-facing surface.

The first complete slice is paper source to paper destination. It must replicate DAX to GER40 and DJ to WS30, skip ES before destination write, remain idempotent when a fill is replayed, and persist the input, outbound request, destination response, timestamps, and `desfase_ms`.

Technical artifacts are written in English. Product UI is Spanish.
