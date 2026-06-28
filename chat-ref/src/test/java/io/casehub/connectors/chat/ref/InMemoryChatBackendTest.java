package io.casehub.connectors.chat.ref;

class InMemoryChatBackendTest extends ChatBackendContract {
    @Override
    protected ChatBackend createBackend() {
        return new InMemoryChatBackend();
    }
}
