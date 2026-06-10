# Protocols — casehub-connectors

Navigation hub. Rules are organised by scope; read the sub-index for the full list.

## connectors/ — repo-specific rules

| File | Rule Summary | Applies To |
|------|-------------|------------|
| [connectors/shared-http-client.md](connectors/shared-http-client.md) | Use HttpHelper.CLIENT, not new HttpClient instances | Any class making outbound HTTP calls |
| [connectors/inbound-connector-id-constants.md](connectors/inbound-connector-id-constants.md) | Connector IDs are constants in InboundConnectorIds, not strings | Connector implementations and downstream routing |
| [connectors/paginating-client-fail-soft.md](connectors/paginating-client-fail-soft.md) | Paginating HTTP methods return partial results + WARNING on failure, never empty list | Any method issuing multiple HTTP requests to paginate a resource |

Full listing: [connectors/INDEX.md](connectors/INDEX.md)
