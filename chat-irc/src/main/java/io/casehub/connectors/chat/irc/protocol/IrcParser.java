package io.casehub.connectors.chat.irc.protocol;

import java.util.ArrayList;
import java.util.List;

public final class IrcParser {

    private IrcParser() {}

    public static IrcMessage parse(final String line) {
        String remaining = line;
        String prefix = null;

        if (remaining.startsWith(":")) {
            int space = remaining.indexOf(' ');
            prefix = remaining.substring(1, space);
            remaining = remaining.substring(space + 1);
        }

        String trailing = null;
        int trailingStart = remaining.indexOf(" :");
        if (trailingStart >= 0) {
            trailing = remaining.substring(trailingStart + 2);
            remaining = remaining.substring(0, trailingStart);
        }

        String[] parts = remaining.split(" ");
        String command = parts[0];
        List<String> params = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            params.add(parts[i]);
        }
        if (trailing != null) {
            params.add(trailing);
        }

        return new IrcMessage(prefix, command, List.copyOf(params));
    }

    public static String format(final String command, final String... params) {
        if (params.length == 0) {
            return command;
        }
        StringBuilder sb = new StringBuilder(command);
        for (int i = 0; i < params.length; i++) {
            sb.append(' ');
            if (i == params.length - 1 && params[i].contains(" ")) {
                sb.append(':');
            }
            sb.append(params[i]);
        }
        return sb.toString();
    }
}
