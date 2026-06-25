package io.casehub.connectors.chat.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.Attachment;

class ChatContentTest {

    @Test
    void textIsRequired() {
        assertThatThrownBy(() -> new ChatContent(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("text");
    }

    @Test
    void convenienceConstructorSetsDefaults() {
        ChatContent content = new ChatContent("hello");
        assertThat(content.text()).isEqualTo("hello");
        assertThat(content.markdown()).isNull();
        assertThat(content.attachments()).isEmpty();
    }

    @Test
    void nullAttachmentsBecomesEmptyList() {
        ChatContent content = new ChatContent("hello", null, null);
        assertThat(content.attachments()).isEmpty();
    }

    @Test
    void attachmentsAreDefensivelyCopied() {
        List<Attachment> mutable = new ArrayList<>();
        mutable.add(new Attachment("f.txt", "text/plain", new byte[]{1}));
        ChatContent content = new ChatContent("hello", null, mutable);
        mutable.add(new Attachment("g.txt", "text/plain", new byte[]{2}));
        assertThat(content.attachments()).hasSize(1);
    }
}
