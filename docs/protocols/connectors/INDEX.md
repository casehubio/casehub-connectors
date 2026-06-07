# Protocols — connectors

Rules specific to the casehub-connectors repo.

| File | Rule Summary | Applies To |
|------|-------------|------------|
| [shared-http-client.md](shared-http-client.md) | Use HttpHelper.CLIENT, not new HttpClient instances | Any class making outbound HTTP calls |
| [inbound-connector-id-constants.md](inbound-connector-id-constants.md) | Connector IDs are constants in InboundConnectorIds, not strings | Connector implementations and downstream routing code |
