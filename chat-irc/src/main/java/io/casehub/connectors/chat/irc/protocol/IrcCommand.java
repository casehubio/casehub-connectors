package io.casehub.connectors.chat.irc.protocol;

public final class IrcCommand {

    public static final String RPL_WELCOME = "001";
    public static final String RPL_NAMREPLY = "353";
    public static final String RPL_ENDOFNAMES = "366";
    public static final String RPL_LIST = "322";
    public static final String RPL_LISTEND = "323";

    private IrcCommand() {}
}
