// chat-demo/src/main/webui/src/qhorus/events.test.ts
import { describe, it, expect, vi } from 'vitest';
import { emitChatEvent, ChatEventTopics } from './events.js';

describe('emitChatEvent', () => {
  it('dispatches pages-event with topic and payload', () => {
    const target = document.createElement('div');
    const handler = vi.fn();
    target.addEventListener('pages-event', handler);

    emitChatEvent(target, ChatEventTopics.SELECT_CHANNEL, { channelId: 'ch-1' });

    expect(handler).toHaveBeenCalledOnce();
    const event = handler.mock.calls[0][0] as CustomEvent;
    expect(event.detail.topic).toBe('chat:select-channel');
    expect(event.detail.payload).toEqual({ channelId: 'ch-1' });
  });

  it('bubbles and composes', () => {
    const target = document.createElement('div');
    const handler = vi.fn();
    document.body.appendChild(target);
    document.body.addEventListener('pages-event', handler);

    emitChatEvent(target, ChatEventTopics.SEND_MESSAGE, { channelId: 'ch-1', content: 'hello' });

    expect(handler).toHaveBeenCalledOnce();
    document.body.removeChild(target);
    document.body.removeEventListener('pages-event', handler);
  });

  it('all topic constants have chat: prefix', () => {
    for (const topic of Object.values(ChatEventTopics)) {
      expect(topic).toMatch(/^chat:/);
    }
  });

  it('all topic constants have exact string values', () => {
    expect(ChatEventTopics.SEND_MESSAGE).toBe('chat:send-message');
    expect(ChatEventTopics.REACT).toBe('chat:react');
    expect(ChatEventTopics.UNREACT).toBe('chat:unreact');
    expect(ChatEventTopics.CREATE_CHANNEL).toBe('chat:create-channel');
    expect(ChatEventTopics.DELETE_CHANNEL).toBe('chat:delete-channel');
    expect(ChatEventTopics.SELECT_CHANNEL).toBe('chat:select-channel');
    expect(ChatEventTopics.SELECT_TOPIC).toBe('chat:select-topic');
    expect(ChatEventTopics.RESOLVE_TOPIC).toBe('chat:resolve-topic');
    expect(ChatEventTopics.MESSAGE_SELECTED).toBe('chat:message-selected');
  });

  it('emitted event has bubbles=true and composed=true', () => {
    const target = document.createElement('div');
    const handler = vi.fn();
    target.addEventListener('pages-event', handler);

    emitChatEvent(target, ChatEventTopics.SELECT_CHANNEL, { channelId: 'ch-1' });

    const event = handler.mock.calls[0][0] as CustomEvent;
    expect(event.bubbles).toBe(true);
    expect(event.composed).toBe(true);
  });

  it('emits event with null payload without throwing', () => {
    const target = document.createElement('div');
    const handler = vi.fn();
    target.addEventListener('pages-event', handler);

    expect(() => emitChatEvent(target, ChatEventTopics.SELECT_CHANNEL, null as any)).not.toThrow();
    expect(handler).toHaveBeenCalledOnce();
  });

  it('emits event with undefined payload without throwing', () => {
    const target = document.createElement('div');
    const handler = vi.fn();
    target.addEventListener('pages-event', handler);

    expect(() => emitChatEvent(target, ChatEventTopics.SELECT_CHANNEL, undefined as any)).not.toThrow();
    expect(handler).toHaveBeenCalledOnce();
  });
});
