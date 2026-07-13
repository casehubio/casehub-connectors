import { describe, it, expect, afterEach } from 'vitest';
import './qhorus-channel-feed.js';
import '../primitives/qhorus-message.js';
import '../primitives/qhorus-thread.js';
import '../primitives/qhorus-reaction-bar.js';
import type { QhorusMessage } from '../types.js';

function msg(id: string, overrides: Partial<QhorusMessage> = {}): QhorusMessage {
  return {
    id, channelId: 'ch-1', sender: 'agent-a', messageType: 'EVENT',
    actorType: 'AGENT', content: `Message ${id}`, topic: 'General', replyCount: 0,
    artefactRefs: [], createdAt: '2026-07-07T12:00:00Z', ...overrides,
  };
}

afterEach(() => { document.body.innerHTML = ''; });

describe('qhorus-channel-feed', () => {
  it('renders messages chronologically', async () => {
    const el = document.createElement('qhorus-channel-feed') as any;
    el.messages = [msg('1'), msg('2'), msg('3')];
    document.body.appendChild(el);
    await el.updateComplete;

    const msgs = el.shadowRoot!.querySelectorAll('qhorus-message');
    expect(msgs.length).toBe(3);
  });

  it('groups consecutive messages from same sender within 2 min', async () => {
    const el = document.createElement('qhorus-channel-feed') as any;
    el.messages = [
      msg('1', { sender: 'alice', createdAt: '2026-07-07T12:00:00Z' }),
      msg('2', { sender: 'alice', createdAt: '2026-07-07T12:00:30Z' }),
      msg('3', { sender: 'bob', createdAt: '2026-07-07T12:01:00Z' }),
    ];
    document.body.appendChild(el);
    await el.updateComplete;

    const headers = el.shadowRoot!.querySelectorAll('.message-group-header');
    expect(headers.length).toBe(2);
  });

  it('splits sender groups when messages >2min apart', async () => {
    const el = document.createElement('qhorus-channel-feed') as any;
    el.messages = [
      msg('1', { sender: 'alice', createdAt: '2026-07-07T12:00:00Z' }),
      msg('2', { sender: 'alice', createdAt: '2026-07-07T12:02:01Z' }),
    ];
    document.body.appendChild(el);
    await el.updateComplete;

    const headers = el.shadowRoot!.querySelectorAll('.message-group-header');
    expect(headers.length).toBe(2);
  });

  it('renders empty state when messages array is empty', async () => {
    const el = document.createElement('qhorus-channel-feed') as any;
    el.messages = [];
    document.body.appendChild(el);
    await el.updateComplete;

    const emptyDiv = el.shadowRoot!.querySelector('.empty');
    expect(emptyDiv).toBeTruthy();
    expect(emptyDiv!.textContent!.trim()).toBe('No messages yet');
  });

  it('does not render a mode toggle', async () => {
    const el = document.createElement('qhorus-channel-feed') as any;
    el.messages = [msg('1')];
    document.body.appendChild(el);
    await el.updateComplete;

    expect(el.shadowRoot!.querySelector('.mode-toggle')).toBeNull();
    expect(el.shadowRoot!.querySelector('.toolbar')).toBeNull();
  });

  it('separates replies from roots and renders thread inline', async () => {
    const el = document.createElement('qhorus-channel-feed') as any;
    el.messages = [
      msg('root', { sender: 'alice' }),
      msg('reply1', { sender: 'bob', inReplyTo: 'root' }),
      msg('reply2', { sender: 'carol', inReplyTo: 'root' }),
      msg('standalone', { sender: 'dave' }),
    ];
    document.body.appendChild(el);
    await el.updateComplete;

    const threads = el.shadowRoot!.querySelectorAll('qhorus-thread');
    expect(threads.length).toBe(1);
    const thread = threads[0] as any;
    expect(thread.rootMessage.id).toBe('root');
    expect(thread.replies.length).toBe(2);
  });

  it('does not render thread for messages without replies', async () => {
    const el = document.createElement('qhorus-channel-feed') as any;
    el.messages = [
      msg('1', { sender: 'alice', createdAt: '2026-07-07T12:00:00Z' }),
      msg('2', { sender: 'bob', createdAt: '2026-07-07T12:01:00Z' }),
    ];
    document.body.appendChild(el);
    await el.updateComplete;

    const threads = el.shadowRoot!.querySelectorAll('qhorus-thread');
    expect(threads.length).toBe(0);
    const groups = el.shadowRoot!.querySelectorAll('.message-group');
    expect(groups.length).toBe(2);
  });

  it('replies are excluded from root sender grouping', async () => {
    const el = document.createElement('qhorus-channel-feed') as any;
    el.messages = [
      msg('m1', { sender: 'alice', createdAt: '2026-07-07T12:00:00Z' }),
      msg('r1', { sender: 'alice', inReplyTo: 'm1', createdAt: '2026-07-07T12:00:10Z' }),
      msg('m2', { sender: 'bob', createdAt: '2026-07-07T12:01:00Z' }),
    ];
    document.body.appendChild(el);
    await el.updateComplete;

    // Only m1 and m2 are roots — r1 is a reply, not in the sender groups
    const groups = el.shadowRoot!.querySelectorAll('.message-group');
    expect(groups.length).toBe(2);
  });

  it('does not emit chat:message-selected on message item click', async () => {
    const el = document.createElement('qhorus-channel-feed') as any;
    el.messages = [msg('m1', { sender: 'alice' })];
    document.body.appendChild(el);
    await el.updateComplete;

    let eventFired = false;
    el.addEventListener('pages-event', (e: any) => {
      if (e.detail.topic === 'chat:message-selected') eventFired = true;
    });

    const messageItem = el.shadowRoot!.querySelector('.message-item') as HTMLElement;
    messageItem.click();
    expect(eventFired).toBe(false);
  });

  it('passes channelName to qhorus-message elements', async () => {
    const el = document.createElement('qhorus-channel-feed') as any;
    el.messages = [msg('m1', { sender: 'alice' })];
    el.channelName = 'general';
    document.body.appendChild(el);
    await el.updateComplete;

    const msgEl = el.shadowRoot!.querySelector('qhorus-message') as any;
    expect(msgEl.channelName).toBe('general');
  });

  it('passes parentMessage for reply messages in thread', async () => {
    const el = document.createElement('qhorus-channel-feed') as any;
    const root = msg('root', { sender: 'alice', content: 'Root message' });
    const reply = msg('reply', { sender: 'bob', inReplyTo: 'root' });
    el.messages = [root, reply];
    document.body.appendChild(el);
    await el.updateComplete;

    const thread = el.shadowRoot!.querySelector('qhorus-thread') as any;
    expect(thread).toBeTruthy();
    expect(thread.rootMessage.id).toBe('root');
  });

  it('filters reactions per message correctly', async () => {
    const el = document.createElement('qhorus-channel-feed') as any;
    el.messages = [
      msg('m1', { sender: 'alice' }),
      msg('m2', { sender: 'bob' }),
    ];
    el.reactions = [
      { messageId: 'm1', emoji: '👍', users: ['user1'] },
      { messageId: 'm1', emoji: '❤️', users: ['user2'] },
      { messageId: 'm2', emoji: '🔥', users: ['user3'] },
    ];
    document.body.appendChild(el);
    await el.updateComplete;

    const messages = el.shadowRoot!.querySelectorAll('qhorus-message');
    expect(messages.length).toBe(2);
    expect((messages[0] as any).reactions.length).toBe(2);
    expect((messages[1] as any).reactions.length).toBe(1);
  });
});
