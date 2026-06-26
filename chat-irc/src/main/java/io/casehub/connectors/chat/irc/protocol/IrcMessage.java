package io.casehub.connectors.chat.irc.protocol;

import java.util.List;

public record IrcMessage(String prefix, String command, List<String> params) {}
