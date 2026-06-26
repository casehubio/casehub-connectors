package io.casehub.connectors.chat.irc.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmbeddedIrcServerTest {

    private EmbeddedIrcServer server;

    @BeforeEach
    void setUp() {
        server = new EmbeddedIrcServer(0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void connectAndReceiveWelcome() throws Exception {
        try (Socket socket = new Socket("localhost", server.getPort());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))) {
            out.println("NICK testbot");
            out.println("USER testbot 0 * :Test Bot");
            String welcome = in.readLine();
            assertThat(welcome).contains("001").contains("testbot");
        }
    }

    @Test
    void joinChannelReceivesNamesReply() throws Exception {
        try (Socket socket = new Socket("localhost", server.getPort());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))) {
            out.println("NICK testbot");
            out.println("USER testbot 0 * :Test Bot");
            in.readLine(); // 001 welcome
            out.println("JOIN #test");
            String namReply = in.readLine();
            assertThat(namReply).contains("353");
            String endOfNames = in.readLine();
            assertThat(endOfNames).contains("366");
        }
    }

    @Test
    void privmsgIsRecorded() throws Exception {
        try (Socket socket = new Socket("localhost", server.getPort());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))) {
            out.println("NICK testbot");
            out.println("USER testbot 0 * :Test Bot");
            in.readLine(); // 001
            out.println("JOIN #test");
            in.readLine(); // 353
            in.readLine(); // 366
            out.println("PRIVMSG #test :hello world");
            Thread.sleep(100);
            assertThat(server.getReceivedMessages()).hasSize(1);
            assertThat(server.getReceivedMessages().get(0).text()).isEqualTo("hello world");
        }
    }

    @Test
    void listReturnsJoinedChannels() throws Exception {
        try (Socket socket = new Socket("localhost", server.getPort());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))) {
            out.println("NICK testbot");
            out.println("USER testbot 0 * :Test Bot");
            in.readLine(); // 001
            out.println("JOIN #alpha");
            in.readLine(); in.readLine(); // 353, 366
            out.println("JOIN #beta");
            in.readLine(); in.readLine(); // 353, 366
            out.println("LIST");
            String list1 = in.readLine();
            assertThat(list1).contains("322");
            String list2 = in.readLine();
            assertThat(list2).contains("322");
            String listEnd = in.readLine();
            assertThat(listEnd).contains("323");
        }
    }

    @Test
    void sendToChannelDeliversToClient() throws Exception {
        try (Socket socket = new Socket("localhost", server.getPort());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))) {
            out.println("NICK testbot");
            out.println("USER testbot 0 * :Test Bot");
            in.readLine(); // 001
            out.println("JOIN #test");
            in.readLine(); in.readLine(); // 353, 366
            server.sendToChannel("#test", "externaluser", "injected message");
            String received = in.readLine();
            assertThat(received).contains("PRIVMSG").contains("#test").contains("injected message");
        }
    }

    @Test
    void pingReceivesPong() throws Exception {
        try (Socket socket = new Socket("localhost", server.getPort());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))) {
            out.println("NICK testbot");
            out.println("USER testbot 0 * :Test Bot");
            in.readLine(); // 001
            out.println("PING :test123");
            String pong = in.readLine();
            assertThat(pong).isEqualTo("PONG :test123");
        }
    }
}
