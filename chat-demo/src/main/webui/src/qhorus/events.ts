// chat-demo/src/main/webui/src/qhorus/events.ts
import type { QhorusMessage, MessageType, ArtefactRef } from './types.js';

export const ChatEventTopics = {
  SEND_MESSAGE: 'chat:send-message',
  REACT: 'chat:react',
  UNREACT: 'chat:unreact',
  CREATE_CHANNEL: 'chat:create-channel',
  DELETE_CHANNEL: 'chat:delete-channel',
  SELECT_CHANNEL: 'chat:select-channel',
  SELECT_TOPIC: 'chat:select-topic',
  RESOLVE_TOPIC: 'chat:resolve-topic',
  MESSAGE_SELECTED: 'chat:message-selected',
} as const;

export interface SendMessagePayload {
  readonly channelId: string;
  readonly content: string;
  readonly topic?: string;
  readonly inReplyTo?: string;
  readonly speechAct?: MessageType;
  readonly artefactRefs?: readonly ArtefactRef[];
}

export interface ReactPayload {
  readonly messageId: string;
  readonly emoji: string;
}

export interface CreateChannelPayload {
  readonly name: string;
  readonly description?: string;
  readonly spaceId?: string;
  readonly semantic?: string;
}

export interface SelectChannelPayload {
  readonly channelId: string;
}

export interface MessageSelectedPayload {
  readonly message: QhorusMessage;
}

export function emitChatEvent<T>(
  target: EventTarget,
  topic: string,
  payload: T,
): void {
  target.dispatchEvent(
    new CustomEvent('pages-event', {
      bubbles: true,
      composed: true,
      detail: { topic, payload },
    }),
  );
}
