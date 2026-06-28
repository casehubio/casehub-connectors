package io.casehub.connectors.chat.demo;

import jakarta.inject.Inject;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;

@WebSocket(path = "/ws/chat")
public class ChatWebSocket {

    @Inject
    ChatWebSocketBroadcaster broadcaster;

    @OnOpen
    public String onOpen(final WebSocketConnection connection) {
        broadcaster.addConnection(connection);
        return broadcaster.buildSnapshot();
    }

    @OnClose
    public void onClose(final WebSocketConnection connection) {
        broadcaster.removeConnection(connection);
    }
}
