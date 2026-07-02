package io.casehub.connectors.chat.discord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.casehub.connectors.Attachment;
import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.chat.degraded.*;
import io.casehub.connectors.chat.degraded.NoOpMemberManagement;
import io.casehub.connectors.chat.model.*;
import io.casehub.connectors.chat.spi.*;
import io.casehub.connectors.discord.DiscordClient;
import io.casehub.connectors.discord.DiscordGatewayPresenceCache;
import io.casehub.connectors.discord.model.*;

@ApplicationScoped
public class DiscordChatPlatform implements ChatPlatform {

    private static final Logger LOG = Logger.getLogger(DiscordChatPlatform.class.getName());
    private static final long DISCORD_EPOCH = 1420070400000L;
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final long VIEW_CHANNEL_BIT = 1L << 10;

    private static final Set<Class<?>> NATIVE_CAPABILITIES = Set.of(
            Messaging.class,
            Threading.class,
            Discovery.class,
            Reactions.class,
            Presence.class,
            Members.class,
            ChannelManagement.class,
            MessageHistory.class);

    private final DiscordClient client;
    private final DiscordGatewayPresenceCache presenceCache;
    private final String token;
    private final String guildId;

    private Messaging messaging;
    private Threading threading;
    private Discovery discovery;
    private Reactions reactions;
    private Presence presence;
    private Members members;
    private ChannelManagement channelManagement;
    private final MemberManagement memberManagement = new NoOpMemberManagement();
    private MessageHistory messageHistory;

    /**
     * CDI constructor.
     */
    @Inject
    public DiscordChatPlatform(
            final DiscordClient client,
            final DiscordGatewayPresenceCache presenceCache,
            @ConfigProperty(name = "casehub.discord.token", defaultValue = "") final String token,
            @ConfigProperty(name = "casehub.discord.guild-id", defaultValue = "") final String guildId) {
        this.client = client;
        this.presenceCache = presenceCache;
        this.token = token;
        this.guildId = guildId;
    }

    /**
     * Initialize capabilities. If token or guild-id is blank, use degraded/no-op implementations.
     */
    @PostConstruct
    void init() {
        if (token.isBlank() || guildId.isBlank()) {
            LOG.warning("discord: token or guild-id not configured, platform inactive");
            this.messaging = (channel, content) -> SendResult.failure("Discord not configured");
            this.threading = new ChannelFallbackThreading(this.messaging);
            this.discovery = new EmptyDiscovery();
            this.reactions = new NoOpReactions();
            this.presence = new UnknownPresence();
            this.members = new EmptyMembers();
            this.channelManagement = new NoOpChannelManagement();
            this.messageHistory = new EmptyMessageHistory();
        } else {
            this.messaging = this::sendMessage;
            this.threading = this::sendReply;
            this.discovery = this::listChannels;
            this.reactions = new DiscordReactions();
            this.presence = new DiscordPresence();
            this.members = this::listMembers;
            this.channelManagement = new DiscordChannelManagement();
            this.messageHistory = this::getMessageHistory;
        }
    }

    @Override
    public String id() {
        return InboundConnectorTypes.DISCORD;
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
        return NATIVE_CAPABILITIES.contains(capability);
    }

    // Messaging implementation
    private SendResult sendMessage(final ChatChannelRef channel, final ChatContent content) {
        final String messageContent = extractContent(content);
        final List<DiscordEmbed> embeds;
        if (!content.cards().isEmpty()) {
            final SendResult validation = validateEmbeds(content.cards());
            if (validation != null) return validation;
            embeds = toEmbeds(content.cards());
        } else {
            embeds = List.of();
            if (messageContent.length() > MAX_CONTENT_LENGTH) {
                return SendResult.failure("Content exceeds Discord's 2000-character limit");
            }
        }

        final PostResult result = embeds.isEmpty()
                ? client.sendMessage(token, channel.id(), messageContent)
                : client.sendMessage(token, channel.id(), messageContent, embeds);

        if (!result.ok()) {
            return SendResult.failure(result.error());
        }

        return SendResult.success(
                new ChatMessageRef(channel, result.messageId()),
                Instant.now());
    }

    // Threading implementation
    private SendResult sendReply(final ChatMessageRef parent, final ChatContent content) {
        final String messageContent = extractContent(content);
        final List<DiscordEmbed> embeds;
        if (!content.cards().isEmpty()) {
            final SendResult validation = validateEmbeds(content.cards());
            if (validation != null) return validation;
            embeds = toEmbeds(content.cards());
        } else {
            embeds = List.of();
            if (messageContent.length() > MAX_CONTENT_LENGTH) {
                return SendResult.failure("Content exceeds Discord's 2000-character limit");
            }
        }

        final PostResult result = embeds.isEmpty()
                ? client.sendReply(token, parent.channel().id(), messageContent, parent.messageId())
                : client.sendReply(token, parent.channel().id(), messageContent, parent.messageId(), embeds);

        if (!result.ok()) {
            return SendResult.failure(result.error());
        }

        return SendResult.success(
                new ChatMessageRef(parent.channel(), result.messageId()),
                Instant.now());
    }

    // Discovery implementation
    private List<Channel> listChannels() {
        final List<DiscordChannel> channels = client.listGuildChannels(token);
        final DiscordGuild guild = client.getGuild(token, true);
        final Integer memberCount = guild != null ? guild.approximateMemberCount() : null;

        return channels.stream()
                .filter(ch -> isTextChannel(ch.type()))
                .map(ch -> toChannel(ch, memberCount))
                .toList();
    }

    private boolean isTextChannel(final int type) {
        // 0=GUILD_TEXT, 5=GUILD_ANNOUNCEMENT, 10=ANNOUNCEMENT_THREAD, 11=PUBLIC_THREAD, 12=PRIVATE_THREAD
        // Exclude 15=GUILD_FORUM
        return type == 0 || type == 5 || type == 10 || type == 11 || type == 12;
    }

    private Channel toChannel(final DiscordChannel dc, final Integer memberCount) {
        return new Channel(
                new ChatChannelRef(dc.id()),
                dc.name(),
                dc.topic(),
                null, // Discord has no separate description
                isPrivateChannel(dc),
                memberCount);
    }

    private boolean isPrivateChannel(final DiscordChannel channel) {
        return channel.permissionOverwrites().stream()
                .anyMatch(po -> po.type() == 0 // role type
                        && po.id().equals(guildId) // @everyone role
                        && (po.deny() & VIEW_CHANNEL_BIT) != 0); // VIEW_CHANNEL denied
    }

    // Reactions implementation
    private class DiscordReactions implements Reactions {
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
            return client.listReactionEmoji(token, messageRef.channel().id(), messageRef.messageId());
        }
    }

    // Presence implementation
    private class DiscordPresence implements Presence {
        @Override
        public PresenceStatus of(final MemberRef member) {
            final String status = presenceCache.get(member.id());
            return switch (status) {
                case "online" -> PresenceStatus.ONLINE;
                case "idle" -> PresenceStatus.AWAY;
                case "dnd" -> PresenceStatus.DND;
                case "offline" -> PresenceStatus.OFFLINE;
                default -> PresenceStatus.UNKNOWN;
            };
        }

        @Override
        public void set(final MemberRef member, final PresenceStatus status) {
            LOG.warning("Discord does not support setting another user's presence status");
        }
    }

    // Members implementation
    private List<Member> listMembers(final ChatChannelRef channel) {
        final List<DiscordMember> members = client.listGuildMembers(token, 1000, null);

        return members.stream()
                .map(this::toMember)
                .toList();
    }

    private Member toMember(final DiscordMember dm) {
        final String displayName = dm.nick() != null ? dm.nick()
                : (dm.user().globalName() != null ? dm.user().globalName() : dm.user().username());
        return new Member(new MemberRef(dm.user().id()), displayName);
    }

    // ChannelManagement implementation
    private class DiscordChannelManagement implements ChannelManagement {
        @Override
        public Channel create(final String name, final String topic, final String description, final boolean isPrivate) {
            final DiscordChannel dc = client.createChannel(token, name, topic, 0, false, isPrivate);
            if (dc == null) {
                throw new IllegalStateException("Channel creation failed");
            }
            return toChannel(dc, null);
        }

        @Override
        public void delete(final String channelId) {
            client.deleteChannel(token, channelId);
        }

        @Override
        public Optional<Channel> find(final String channelId) {
            final DiscordChannel dc = client.getChannel(token, channelId);
            if (dc == null) {
                return Optional.empty();
            }
            return Optional.of(toChannel(dc, null));
        }
    }

    // MessageHistory implementation
    private List<ReceivedMessage> getMessageHistory(final ChatChannelRef channel, final Instant since) {
        if (since.toEpochMilli() < DISCORD_EPOCH) {
            LOG.warning("MessageHistory: 'since' timestamp before Discord epoch, returning empty");
            return List.of();
        }

        final long syntheticSnowflake = (since.toEpochMilli() - DISCORD_EPOCH) << 22;
        final String afterId = String.valueOf(syntheticSnowflake);

        final List<DiscordMessage> messages = client.getMessages(token, channel.id(), afterId, 100);

        return messages.stream()
                .map(dm -> toReceivedMessage(channel, dm))
                .toList();
    }

    private ReceivedMessage toReceivedMessage(final ChatChannelRef channel, final DiscordMessage dm) {
        final ChatMessageRef messageRef = new ChatMessageRef(channel, dm.id());
        final ChatMessageRef parentRef = dm.type() == 19 && dm.referencedMessageId() != null
                ? new ChatMessageRef(channel, dm.referencedMessageId())
                : null;

        final List<Attachment> attachments = new ArrayList<>();
        for (final DiscordAttachment da : dm.attachments()) {
            final Attachment downloaded = client.downloadAttachment(da);
            if (downloaded != null) {
                attachments.add(downloaded);
            }
        }

        return new ReceivedMessage(
                InboundConnectorTypes.DISCORD,
                channel,
                messageRef,
                parentRef,
                new MemberRef(dm.author().id()),
                new ChatContent(dm.content(), null, attachments, List.of()),
                dm.timestamp());
    }

    private List<DiscordEmbed> toEmbeds(final List<RichCard> cards) {
        return cards.stream().map(this::toEmbed).toList();
    }

    private DiscordEmbed toEmbed(final RichCard card) {
        final List<DiscordEmbed.Field> fields = card.fields().stream()
                .map(f -> new DiscordEmbed.Field(f.name(), f.value(), f.inline()))
                .toList();
        final DiscordEmbed.Footer footer = card.footer() != null
                ? new DiscordEmbed.Footer(card.footer()) : null;
        final DiscordEmbed.Author author = card.author() != null
                ? new DiscordEmbed.Author(card.author()) : null;
        return new DiscordEmbed(card.title(), card.description(), card.url(), card.color(),
                fields, card.thumbnailUrl(), card.imageUrl(), footer, author);
    }

    /** Returns {@code null} if valid, or {@link SendResult#failure} with the reason. */
    private SendResult validateEmbeds(final List<RichCard> cards) {
        if (cards.size() > 10) {
            return SendResult.failure("Discord allows at most 10 embeds per message");
        }
        long totalChars = 0;
        for (final RichCard card : cards) {
            if (card.title() != null && card.title().length() > 256) {
                return SendResult.failure("Embed title exceeds 256 characters");
            }
            if (card.description() != null && card.description().length() > 4096) {
                return SendResult.failure("Embed description exceeds 4096 characters");
            }
            if (card.footer() != null && card.footer().length() > 2048) {
                return SendResult.failure("Embed footer exceeds 2048 characters");
            }
            if (card.author() != null && card.author().length() > 256) {
                return SendResult.failure("Embed author exceeds 256 characters");
            }
            if (card.fields().size() > 25) {
                return SendResult.failure("Embed exceeds 25 fields");
            }
            if (card.url() != null && card.title() == null) {
                return SendResult.failure("Embed url requires title");
            }
            totalChars += (card.title() != null ? card.title().length() : 0)
                    + (card.description() != null ? card.description().length() : 0)
                    + (card.footer() != null ? card.footer().length() : 0)
                    + (card.author() != null ? card.author().length() : 0);
            for (final RichCard.Field f : card.fields()) {
                totalChars += f.name().length() + f.value().length();
            }
        }
        if (totalChars > 6000) {
            return SendResult.failure("Total embed content exceeds 6000 characters");
        }
        return null;
    }

    private String extractContent(final ChatContent content) {
        return content.markdown() != null ? content.markdown() : content.text();
    }
}
