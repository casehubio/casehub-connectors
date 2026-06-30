package io.casehub.connectors.chat.model;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RichCardTest {

    @Test
    void requiresTitleOrDescription() {
        assertThatThrownBy(() -> new RichCard(null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title or description");
    }

    @Test
    void titleOnlyIsValid() {
        final var card = new RichCard("title", null, null, null, null, null, null, null, null);
        assertThat(card.title()).isEqualTo("title");
        assertThat(card.description()).isNull();
        assertThat(card.fields()).isEmpty();
    }

    @Test
    void descriptionOnlyIsValid() {
        final var card = new RichCard(null, "desc", null, null, null, null, null, null, null);
        assertThat(card.description()).isEqualTo("desc");
    }

    @Test
    void fieldsDefensiveCopy() {
        final var mutable = new java.util.ArrayList<>(List.of(
                new RichCard.Field("k", "v", false)));
        final var card = new RichCard("t", null, null, null, mutable, null, null, null, null);
        mutable.add(new RichCard.Field("k2", "v2", true));
        assertThat(card.fields()).hasSize(1);
    }

    @Test
    void nullFieldsBecomesEmptyList() {
        final var card = new RichCard("t", null, null, null, null, null, null, null, null);
        assertThat(card.fields()).isEmpty();
    }

    @Test
    void builderProducesValidCard() {
        final var card = RichCard.builder()
                .title("Deploy Summary")
                .description("3 services updated")
                .color(0x00FF00)
                .fields(List.of(new RichCard.Field("env", "prod", true)))
                .footer("footer")
                .author("bot")
                .build();

        assertThat(card.title()).isEqualTo("Deploy Summary");
        assertThat(card.description()).isEqualTo("3 services updated");
        assertThat(card.color()).isEqualTo(0x00FF00);
        assertThat(card.fields()).hasSize(1);
        assertThat(card.fields().getFirst().name()).isEqualTo("env");
        assertThat(card.footer()).isEqualTo("footer");
        assertThat(card.author()).isEqualTo("bot");
    }

    @Test
    void builderRequiresTitleOrDescription() {
        assertThatThrownBy(() -> RichCard.builder().build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builderWithAllFields() {
        final var card = RichCard.builder()
                .title("t")
                .description("d")
                .url("https://example.com")
                .color(16711680)
                .thumbnailUrl("https://img/thumb.png")
                .imageUrl("https://img/full.png")
                .footer("ft")
                .author("au")
                .fields(List.of(
                        new RichCard.Field("a", "1", true),
                        new RichCard.Field("b", "2", false)))
                .build();

        assertThat(card.url()).isEqualTo("https://example.com");
        assertThat(card.thumbnailUrl()).isEqualTo("https://img/thumb.png");
        assertThat(card.imageUrl()).isEqualTo("https://img/full.png");
        assertThat(card.fields()).hasSize(2);
    }
}
