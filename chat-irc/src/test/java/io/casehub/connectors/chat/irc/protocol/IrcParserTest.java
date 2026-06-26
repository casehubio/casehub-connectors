package io.casehub.connectors.chat.irc.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IrcParserTest {

    @Test
    void parseServerMessageWithPrefixAndTrailing() {
        IrcMessage msg = IrcParser.parse(":nick!user@host PRIVMSG #channel :hello world");
        assertThat(msg.prefix()).isEqualTo("nick!user@host");
        assertThat(msg.command()).isEqualTo("PRIVMSG");
        assertThat(msg.params()).containsExactly("#channel", "hello world");
    }

    @Test
    void parseNumericReply() {
        IrcMessage msg = IrcParser.parse(":server 001 botname :Welcome to IRC");
        assertThat(msg.prefix()).isEqualTo("server");
        assertThat(msg.command()).isEqualTo("001");
        assertThat(msg.params()).containsExactly("botname", "Welcome to IRC");
    }

    @Test
    void parseWithNoPrefix() {
        IrcMessage msg = IrcParser.parse("PING :server.example.com");
        assertThat(msg.prefix()).isNull();
        assertThat(msg.command()).isEqualTo("PING");
        assertThat(msg.params()).containsExactly("server.example.com");
    }

    @Test
    void parseWithMultipleMiddleParams() {
        IrcMessage msg = IrcParser.parse(":server 353 bot = #channel :nick1 nick2 nick3");
        assertThat(msg.prefix()).isEqualTo("server");
        assertThat(msg.command()).isEqualTo("353");
        assertThat(msg.params()).containsExactly("bot", "=", "#channel", "nick1 nick2 nick3");
    }

    @Test
    void parseWithNoTrailing() {
        IrcMessage msg = IrcParser.parse(":nick!user@host JOIN #channel");
        assertThat(msg.prefix()).isEqualTo("nick!user@host");
        assertThat(msg.command()).isEqualTo("JOIN");
        assertThat(msg.params()).containsExactly("#channel");
    }

    @Test
    void parseWithEmptyTrailing() {
        IrcMessage msg = IrcParser.parse(":nick!user@host PRIVMSG #channel :");
        assertThat(msg.params()).containsExactly("#channel", "");
    }

    @Test
    void formatSimpleCommand() {
        String line = IrcParser.format("NICK", "botname");
        assertThat(line).isEqualTo("NICK botname");
    }

    @Test
    void formatWithTrailingContainingSpaces() {
        String line = IrcParser.format("PRIVMSG", "#channel", "hello world");
        assertThat(line).isEqualTo("PRIVMSG #channel :hello world");
    }

    @Test
    void formatWithSingleParamContainingSpaces() {
        String line = IrcParser.format("QUIT", "goodbye all");
        assertThat(line).isEqualTo("QUIT :goodbye all");
    }

    @Test
    void formatNoParams() {
        String line = IrcParser.format("QUIT");
        assertThat(line).isEqualTo("QUIT");
    }
}
