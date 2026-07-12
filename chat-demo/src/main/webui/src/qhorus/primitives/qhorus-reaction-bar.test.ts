// chat-demo/src/main/webui/src/qhorus/primitives/qhorus-reaction-bar.test.ts
import { describe, it, expect, vi, afterEach } from 'vitest';
import './qhorus-reaction-bar.js';
import type { Reaction } from '../types.js';

function makeReactions(specs: Array<[string, string[]]>): Reaction[] {
  return specs.flatMap(([emoji, actors]) =>
    actors.map(actorId => ({ messageId: 'msg-1', emoji, actorId, createdAt: '2026-07-07T12:00:00Z' }))
  );
}

afterEach(() => { document.body.innerHTML = ''; });

describe('qhorus-reaction-bar', () => {
  it('renders grouped reaction pills', async () => {
    const el = document.createElement('qhorus-reaction-bar') as any;
    el.reactions = makeReactions([['👍', ['a', 'b']], ['❤️', ['a']]]);
    el.messageId = 'msg-1';
    document.body.appendChild(el);
    await el.updateComplete;

    const pills = el.shadowRoot!.querySelectorAll('.reaction-pill');
    expect(pills.length).toBe(2);
    expect(pills[0].textContent).toContain('👍');
    expect(pills[0].textContent).toContain('2');
    expect(pills[1].textContent).toContain('❤️');
    expect(pills[1].textContent).toContain('1');
  });

  it('highlights pills where current user reacted', async () => {
    const el = document.createElement('qhorus-reaction-bar') as any;
    el.reactions = makeReactions([['👍', ['me', 'other']]]);
    el.messageId = 'msg-1';
    el.currentActorId = 'me';
    document.body.appendChild(el);
    await el.updateComplete;

    const pill = el.shadowRoot!.querySelector('.reaction-pill');
    expect(pill!.classList.contains('reacted')).toBe(true);
  });

  it('emits chat:react on click when not reacted', async () => {
    const el = document.createElement('qhorus-reaction-bar') as any;
    el.reactions = makeReactions([['👍', ['other']]]);
    el.messageId = 'msg-1';
    el.currentActorId = 'me';
    document.body.appendChild(el);
    await el.updateComplete;

    const handler = vi.fn();
    el.addEventListener('pages-event', handler);
    el.shadowRoot!.querySelector('.reaction-pill')!.click();

    expect(handler).toHaveBeenCalledOnce();
    expect(handler.mock.calls[0][0].detail.topic).toBe('chat:react');
    expect(handler.mock.calls[0][0].detail.payload).toEqual({ messageId: 'msg-1', emoji: '👍' });
  });

  it('emits chat:unreact on click when already reacted', async () => {
    const el = document.createElement('qhorus-reaction-bar') as any;
    el.reactions = makeReactions([['👍', ['me']]]);
    el.messageId = 'msg-1';
    el.currentActorId = 'me';
    document.body.appendChild(el);
    await el.updateComplete;

    const handler = vi.fn();
    el.addEventListener('pages-event', handler);
    el.shadowRoot!.querySelector('.reaction-pill')!.click();

    expect(handler.mock.calls[0][0].detail.topic).toBe('chat:unreact');
  });

  it('renders nothing when reactions array is empty', async () => {
    const el = document.createElement('qhorus-reaction-bar') as any;
    el.reactions = [];
    el.messageId = 'msg-1';
    document.body.appendChild(el);
    await el.updateComplete;

    const pills = el.shadowRoot!.querySelectorAll('.reaction-pill');
    expect(pills.length).toBe(0);
  });

  it('does not highlight pills when currentActorId is undefined', async () => {
    const el = document.createElement('qhorus-reaction-bar') as any;
    el.reactions = makeReactions([['👍', ['other']]]);
    el.messageId = 'msg-1';
    el.currentActorId = undefined;
    document.body.appendChild(el);
    await el.updateComplete;

    const pill = el.shadowRoot!.querySelector('.reaction-pill');
    expect(pill!.classList.contains('reacted')).toBe(false);
  });

  it('emits event with correct emoji when clicking second pill', async () => {
    const el = document.createElement('qhorus-reaction-bar') as any;
    el.reactions = makeReactions([['👍', ['a']], ['❤️', ['b']]]);
    el.messageId = 'msg-1';
    el.currentActorId = 'me';
    document.body.appendChild(el);
    await el.updateComplete;

    const handler = vi.fn();
    el.addEventListener('pages-event', handler);
    const pills = el.shadowRoot!.querySelectorAll('.reaction-pill');
    pills[1].click();

    expect(handler).toHaveBeenCalledOnce();
    expect(handler.mock.calls[0][0].detail.payload.emoji).toBe('❤️');
  });

  it('event payload emoji matches clicked pill emoji exactly', async () => {
    const el = document.createElement('qhorus-reaction-bar') as any;
    el.reactions = makeReactions([['🚀', ['a']]]);
    el.messageId = 'msg-1';
    el.currentActorId = 'me';
    document.body.appendChild(el);
    await el.updateComplete;

    const handler = vi.fn();
    el.addEventListener('pages-event', handler);
    el.shadowRoot!.querySelector('.reaction-pill')!.click();

    const emoji = handler.mock.calls[0][0].detail.payload.emoji;
    expect(emoji).toBe('🚀');
  });
});
