package io.casehub.connectors.chat.demo;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ChatResourceTest {

    private String channelId;

    @BeforeEach
    void setUp() {
        channelId = given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", "test-" + System.nanoTime(), "topic", "Test", "description", "Desc", "isPrivate", false))
                .post("/api/channels")
                .then().statusCode(200)
                .extract().path("ref.id");
    }

    @Test
    void createAndListChannels() {
        given()
                .get("/api/channels")
                .then().statusCode(200)
                .body("size()", is(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void postAndListMessages() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("text", "hello"))
                .post("/api/channels/{id}/messages", channelId)
                .then().statusCode(200)
                .body("ok", is(true))
                .body("messageId", notNullValue());

        final List<?> messages = given()
                .get("/api/channels/{id}/messages", channelId)
                .then().statusCode(200)
                .extract().jsonPath().getList("$");

        assertThat(messages).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void postReply() {
        final String messageId = given()
                .contentType(ContentType.JSON)
                .body(Map.of("text", "parent"))
                .post("/api/channels/{id}/messages", channelId)
                .then().statusCode(200)
                .extract().path("messageId");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("text", "reply"))
                .post("/api/channels/{channelId}/messages/{messageId}/replies", channelId, messageId)
                .then().statusCode(200)
                .body("ok", is(true));
    }

    @Test
    void addAndListReactions() {
        final String messageId = given()
                .contentType(ContentType.JSON)
                .body(Map.of("text", "react"))
                .post("/api/channels/{id}/messages", channelId)
                .then().statusCode(200)
                .extract().path("messageId");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("emoji", "thumbsup"))
                .post("/api/channels/{channelId}/messages/{messageId}/reactions", channelId, messageId)
                .then().statusCode(200);

        final List<String> reactions = given()
                .get("/api/channels/{channelId}/messages/{messageId}/reactions", channelId, messageId)
                .then().statusCode(200)
                .extract().jsonPath().getList("$");

        assertThat(reactions).contains("thumbsup");
    }

    @Test
    void removeReaction() {
        final String messageId = given()
                .contentType(ContentType.JSON)
                .body(Map.of("text", "react"))
                .post("/api/channels/{id}/messages", channelId)
                .then().statusCode(200)
                .extract().path("messageId");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("emoji", "heart"))
                .post("/api/channels/{channelId}/messages/{messageId}/reactions", channelId, messageId)
                .then().statusCode(200);

        given()
                .delete("/api/channels/{channelId}/messages/{messageId}/reactions/{emoji}",
                        channelId, messageId, "heart")
                .then().statusCode(200);

        final List<String> reactions = given()
                .get("/api/channels/{channelId}/messages/{messageId}/reactions", channelId, messageId)
                .then().statusCode(200)
                .extract().jsonPath().getList("$");

        assertThat(reactions).doesNotContain("heart");
    }

    @Test
    void addAndListMembers() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("memberId", "user1", "displayName", "User One"))
                .post("/api/channels/{id}/members", channelId)
                .then().statusCode(200);

        final List<?> members = given()
                .get("/api/channels/{id}/members", channelId)
                .then().statusCode(200)
                .extract().jsonPath().getList("$");

        assertThat(members).hasSize(1);
    }

    @Test
    void removeMember() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("memberId", "user2", "displayName", "User Two"))
                .post("/api/channels/{id}/members", channelId)
                .then().statusCode(200);

        given()
                .delete("/api/channels/{channelId}/members/{memberId}", channelId, "user2")
                .then().statusCode(200);

        final List<?> members = given()
                .get("/api/channels/{id}/members", channelId)
                .then().statusCode(200)
                .extract().jsonPath().getList("$");

        assertThat(members).isEmpty();
    }

    @Test
    void setAndGetPresence() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("status", "ONLINE"))
                .put("/api/presence/{memberId}", "agent-1")
                .then().statusCode(200);

        given()
                .get("/api/presence/{memberId}", "agent-1")
                .then().statusCode(200)
                .body("status", is("ONLINE"));
    }
}
