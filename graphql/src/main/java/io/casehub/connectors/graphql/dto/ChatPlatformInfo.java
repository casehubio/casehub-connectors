package io.casehub.connectors.graphql.dto;

import java.util.List;

public record ChatPlatformInfo(String id, List<String> capabilities) {
}
