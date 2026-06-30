package io.casehub.connectors.chat.slack;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.chat.degraded.*;
import io.casehub.connectors.chat.model.*;
import io.casehub.connectors.chat.spi.*;
import io.casehub.connectors.slack.bot.SlackBotClient;
import io.casehub.connectors.slack.bot.SlackBotClient.ConversationInfo;
import io.casehub.connectors.slack.bot.SlackBotClient.ConversationResult;
import io.casehub.connectors.slack.bot.SlackBotClient.HistoryMessage;
import io.casehub.connectors.slack.bot.SlackBotClient.HistoryResult;
import io.casehub.connectors.slack.bot.SlackBotClient.PostResult;
import io.casehub.connectors.slack.bot.SlackBotClient.PresenceResult;
import io.casehub.connectors.slack.bot.SlackBotClient.ReactionListResult;
import io.casehub.connectors.slack.bot.SlackBotClient.UserInfo;

@ApplicationScoped
public class SlackChatPlatform implements ChatPlatform {

    private static final Logger LOG = Logger.getLogger(SlackChatPlatform.class.getName());

    private static final Set<Class<?>> NATIVE_CAPABILITIES = Set.of(
            Messaging.class,
            Threading.class,
            Discovery.class,
            Reactions.class,
            Presence.class,
            Members.class,
            ChannelManagement.class,
            MemberManagement.class,
            MessageHistory.class);

    private final SlackBotClient client;
    private final String token;

    private Set<Class<?>> activeCapabilities;
    private Messaging messaging;
    private Threading threading;
    private Discovery discovery;
    private Reactions reactions;
    private Presence presence;
    private Members members;
    private ChannelManagement channelManagement;
    private MemberManagement memberManagement;
    private MessageHistory messageHistory;

    /**
     * CDI constructor.
     */
    @Inject
    public SlackChatPlatform(
            final SlackBotClient client,
            @ConfigProperty(name = "casehub.slack.token", defaultValue = "") final String token) {
        this.client = client;
        this.token = token;
    }

    /**
     * Initialize capabilities. If token is blank, use degraded/no-op implementations.
     */
    @PostConstruct
    void init() {
        if (token.isBlank()) {
            LOG.warning("slack: token not configured, platform inactive");
            this.activeCapabilities = Set.of();
            this.messaging = (channel, content) -> SendResult.failure("Slack not configured");
            this.threading = new ChannelFallbackThreading(this.messaging);
            this.discovery = new EmptyDiscovery();
            this.reactions = new NoOpReactions();
            this.presence = new UnknownPresence();
            this.members = new EmptyMembers();
            this.channelManagement = new NoOpChannelManagement();
            this.memberManagement = new NoOpMemberManagement();
            this.messageHistory = new EmptyMessageHistory();
        } else {
            this.activeCapabilities = NATIVE_CAPABILITIES;
            this.messaging = this::sendMessage;
            this.threading = this::sendReply;
            this.discovery = this::listChannels;
            this.reactions = new SlackReactions();
            this.presence = new SlackPresence();
            this.members = this::listMembers;
            this.channelManagement = new SlackChannelManagement();
            this.memberManagement = new SlackMemberManagement();
            this.messageHistory = this::getMessageHistory;
        }
    }

    @Override
    public String id() {
        return InboundConnectorTypes.SLACK;
    }

    @Override
    public Messaging messaging() {
        return messaging;
    }

    @Override
    public Threading threading() {
        return threading;
    }

    @Override
    public Discovery discovery() {
        return discovery;
    }

    @Override
    public Reactions reactions() {
        return reactions;
    }

    @Override
    public Presence presence() {
        return presence;
    }

    @Override
    public Members members() {
        return members;
    }

    @Override
    public ChannelManagement channelManagement() {
        return channelManagement;
    }

    @Override
    public MemberManagement memberManagement() {
        return memberManagement;
    }

    @Override
    public MessageHistory messageHistory() {
        return messageHistory;
    }

    @Override
    public boolean supports(final Class<?> capability) {
        return activeCapabilities.contains(capability);
    }

    // ── Messaging ───────────────────────────────────────────────────────────────

    private SendResult sendMessage(final ChatChannelRef channel, final ChatContent content) {
        final PostResult result = client.postMessage(token, channel.id(), content.text(), null);
        if (!result.ok()) {
            return SendResult.failure(result.error());
        }
        return SendResult.success(
                new ChatMessageRef(channel, result.ts()),
                parseTs(result.ts()));
    }

    // ── Threading ───────────────────────────────────────────────────────────────

    private SendResult sendReply(final ChatMessageRef parent, final ChatContent content) {
        final PostResult result = client.postMessage(
                token, parent.channel().id(), content.text(), parent.messageId());
        if (!result.ok()) {
            return SendResult.failure(result.error());
        }
        return SendResult.success(
                new ChatMessageRef(parent.channel(), result.ts()),
                parseTs(result.ts()));
    }

    // ── Discovery ───────────────────────────────────────────────────────────────

    private List<Channel> listChannels() {
        return client.listConversations(token).stream()
                .map(SlackChatPlatform::toChannel)
                .toList();
    }

    private static Channel toChannel(final ConversationInfo c) {
        return new Channel(
                new ChatChannelRef(c.id()),
                c.name(),
                c.topic(),
                c.purpose(),
                c.isPrivate());
    }

    // ── Reactions ───────────────────────────────────────────────────────────────

    private class SlackReactions implements Reactions {
        @Override
        public void add(final ChatMessageRef messageRef, final String emoji) {
            client.addReaction(token, messageRef.channel().id(), messageRef.messageId(), emoji);
        }

        @Override
        public void remove(final ChatMessageRef messageRef, final String emoji) {
            client.removeReaction(token, messageRef.channel().id(), messageRef.messageId(), emoji);
        }

        @Override
        public List<String> list(final ChatMessageRef messageRef) {
            final ReactionListResult result = client.getReactions(
                    token, messageRef.channel().id(), messageRef.messageId());
            return result.ok() ? result.emojis() : List.of();
        }
    }

    // ── Presence ────────────────────────────────────────────────────────────────

    private class SlackPresence implements Presence {
        @Override
        public PresenceStatus of(final MemberRef member) {
            final PresenceResult result = client.getPresence(token, member.id());
            if (!result.ok()) {
                return PresenceStatus.UNKNOWN;
            }
            return switch (result.presence()) {
                case "active" -> PresenceStatus.ONLINE;
                case "away" -> PresenceStatus.AWAY;
                default -> PresenceStatus.UNKNOWN;
            };
        }

        @Override
        public void set(final MemberRef member, final PresenceStatus status) {
            LOG.warning("Slack does not support setting another user's presence status");
        }
    }

    // ── Members ─────────────────────────────────────────────────────────────────

    private List<Member> listMembers(final ChatChannelRef channel) {
        final List<String> memberIds = client.listConversationMembers(token, channel.id());
        final List<UserInfo> allUsers = client.listUsers(token);

        // Build lookup map: userId → UserInfo
        final Map<String, UserInfo> userMap = allUsers.stream()
                .collect(Collectors.toMap(UserInfo::id, Function.identity(), (a, b) -> a));

        return memberIds.stream()
                .map(id -> {
                    final UserInfo info = userMap.get(id);
                    final String displayName;
                    if (info != null) {
                        displayName = !info.displayName().isBlank() ? info.displayName()
                                : !info.realName().isBlank() ? info.realName()
                                : id;
                    } else {
                        displayName = id;
                    }
                    return new Member(new MemberRef(id), displayName);
                })
                .toList();
    }

    // ── Channel Management ──────────────────────────────────────────────────────

    private class SlackChannelManagement implements ChannelManagement {
        @Override
        public Channel create(final String name, final String topic,
                              final String description, final boolean isPrivate) {
            final ConversationResult result = client.createConversation(token, name, isPrivate);
            if (!result.ok()) {
                throw new IllegalStateException("Channel creation failed: " + result.error());
            }
            return toChannel(result.info());
        }

        @Override
        public Optional<Channel> find(final String channelId) {
            final ConversationResult result = client.getConversationInfo(token, channelId);
            if (!result.ok()) {
                return Optional.empty();
            }
            return Optional.of(toChannel(result.info()));
        }
    }

    // ── Member Management ───────────────────────────────────────────────────────

    private class SlackMemberManagement implements MemberManagement {
        @Override
        public void add(final ChatChannelRef channel, final Member member) {
            client.inviteToConversation(token, channel.id(), member.ref().id());
        }

        @Override
        public void remove(final ChatChannelRef channel, final MemberRef member) {
            client.kickFromConversation(token, channel.id(), member.id());
        }
    }

    // ── Message History ─────────────────────────────────────────────────────────

    private List<ReceivedMessage> getMessageHistory(final ChatChannelRef channel, final Instant since) {
        final String oldest = since.getEpochSecond() + "."
                + String.format("%06d", since.getNano() / 1000);

        final HistoryResult result = client.getHistory(token, channel.id(), oldest, 100);
        if (!result.ok()) {
            LOG.warning("SlackChatPlatform: getHistory failed — " + result.error());
            return List.of();
        }

        return result.messages().stream()
                .map(m -> toReceivedMessage(channel, m))
                .toList();
    }

    private ReceivedMessage toReceivedMessage(final ChatChannelRef channel, final HistoryMessage m) {
        final ChatMessageRef messageRef = new ChatMessageRef(channel, m.ts());
        final ChatMessageRef parentRef = m.threadTs() != null
                ? new ChatMessageRef(channel, m.threadTs()) : null;
        return new ReceivedMessage(
                InboundConnectorTypes.SLACK,
                channel,
                messageRef,
                parentRef,
                new MemberRef(m.user()),
                new ChatContent(m.text()),
                parseTs(m.ts()));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Parses a Slack {@code ts} string (e.g. {@code "1234567890.123456"}) into an {@link Instant}.
     *
     * <p>The integer part is epoch seconds, the fractional part is microseconds.
     * Multiply microseconds by 1000 to get nanoseconds.
     */
    static Instant parseTs(final String ts) {
        final int dot = ts.indexOf('.');
        if (dot < 0) {
            return Instant.ofEpochSecond(Long.parseLong(ts));
        }
        final long seconds = Long.parseLong(ts.substring(0, dot));
        final long micros = Long.parseLong(ts.substring(dot + 1));
        return Instant.ofEpochSecond(seconds, micros * 1000);
    }
}
