package io.casehub.connectors.chat.irc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.chat.irc.protocol.ChannelInfo;
import io.casehub.connectors.chat.irc.protocol.IrcMessage;
import io.casehub.connectors.chat.irc.test.EmbeddedIrcServer;

class IrcClientTest {

    private EmbeddedIrcServer server;
    private IrcClient client;

    @BeforeEach
    void setUp() {
        server = new EmbeddedIrcServer(0);
        server.start();
        client = new IrcClient("localhost", server.getPort(), "testbot");
    }

    @AfterEach
    void tearDown() {
        client.disconnect();
        server.stop();
    }

    @Test
    void connectAndDisconnect() {
        assertThat(client.connect()).isTrue();
        assertThat(client.isConnected()).isTrue();
        client.disconnect();
        assertThat(client.isConnected()).isFalse();
    }

    @Test
    void joinChannel() {
        client.connect();
        assertThat(client.join("#test")).isTrue();
    }

    @Test
    void sendMessage() throws Exception {
        client.connect();
        client.join("#test");
        assertThat(client.send("#test", "hello")).isTrue();
        // Small delay to allow server to process the message
        Thread.sleep(100);
        assertThat(server.getReceivedMessages()).hasSize(1);
        assertThat(server.getReceivedMessages().get(0).text()).isEqualTo("hello");
    }

    @Test
    void listChannels() {
        client.connect();
        client.join("#alpha");
        client.join("#beta");
        List<ChannelInfo> channels = client.listChannels();
        assertThat(channels).extracting(ChannelInfo::name)
                .containsExactlyInAnyOrder("#alpha", "#beta");
    }

    @Test
    void namesReturnsChannelMembers() {
        client.connect();
        client.join("#test");
        List<String> nicks = client.names("#test");
        assertThat(nicks).contains("testbot");
    }

    @Test
    void receiveCallback() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<IrcMessage> received = new CopyOnWriteArrayList<>();
        client.setMessageCallback(msg -> {
            received.add(msg);
            latch.countDown();
        });
        client.connect();
        client.join("#test");
        server.sendToChannel("#test", "other", "hello from other");
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).params().get(1)).isEqualTo("hello from other");
    }

    @Test
    void sendReturnsFalseWhenNotConnected() {
        assertThat(client.send("#test", "hello")).isFalse();
    }

    @Test
    void listChannelsReturnsEmptyWhenNotConnected() {
        assertThat(client.listChannels()).isEmpty();
    }

    @Test
    void joinReturnsFalseWhenNotConnected() {
        assertThat(client.join("#test")).isFalse();
    }

    @Test
    void readLoopExitsOnServerDisconnect() throws Exception {
        client.connect();
        assertThat(client.isConnected()).isTrue();
        server.stop();
        Thread.sleep(500);
        assertThat(client.isConnected()).isFalse();
    }
}
