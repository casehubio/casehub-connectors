# Protocols — casehub-connectors

Navigation hub. Rules are organised by scope; read the sub-index for the full list.

## connectors/ — repo-specific rules

| File | Rule Summary | Applies To |
|------|-------------|------------|
| [connectors/shared-http-client.md](connectors/shared-http-client.md) | Use HttpHelper.CLIENT, not new HttpClient instances | Any class making outbound HTTP calls |
| [connectors/inbound-connector-id-constants.md](connectors/inbound-connector-id-constants.md) | Connector IDs are constants in InboundConnectorIds, not strings | Connector implementations and downstream routing |

Full listing: [connectors/INDEX.md](connectors/INDEX.md)
