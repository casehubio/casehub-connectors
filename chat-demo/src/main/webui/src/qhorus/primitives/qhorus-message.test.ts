// chat-demo/src/main/webui/src/qhorus/primitives/qhorus-message.test.ts
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import './qhorus-message.js';
import './qhorus-reaction-bar.js';
import type { QhorusMessage } from '../types.js';

function makeMessage(overrides: Partial<QhorusMessage> = {}): QhorusMessage {
  return {
    id: 'msg-1',
    channelId: 'ch-1',
    sender: 'agent-alpha',
    messageType: 'EVENT',
    actorType: 'AGENT',
    content: 'Hello world',
    topic: 'General',
    replyCount: 0,
    artefactRefs: [],
    createdAt: '2026-07-07T12:00:00Z',
    ...overrides,
  };
}

async function renderMessage(props: Record<string, unknown> = {}): Promise<HTMLElement> {
  const el = document.createElement('qhorus-message') as any;
  el.message = makeMessage(props.message as any);
  if (props.reactions) el.reactions = props.reactions;
  if (props.showSpeechAct !== undefined) el.showSpeechAct = props.showSpeechAct;
  if (props.showActorBadge !== undefined) el.showActorBadge = props.showActorBadge;
  if (props.expanded !== undefined) el.expanded = props.expanded;
  document.body.appendChild(el);
  await el.updateComplete;
  return el;
}

afterEach(() => {
  document.body.innerHTML = '';
});

describe('qhorus-message', () => {
  it('renders sender name', async () => {
    const el = await renderMessage();
    const shadow = el.shadowRoot!;
    expect(shadow.textContent).toContain('agent-alpha');
  });

  it('renders message content as markdown', async () => {
    const el = await renderMessage({ message: { content: '**bold** text' } });
    const shadow = el.shadowRoot!;
    expect(shadow.innerHTML).toContain('<strong>bold</strong>');
  });

  it('renders speech act badge by default', async () => {
    const el = await renderMessage({ message: { messageType: 'COMMAND' } });
    const badge = el.shadowRoot!.querySelector('.speech-act-badge');
    expect(badge).toBeTruthy();
    expect(badge!.textContent!.trim()).toBe('COMMAND');
  });

  it('hides speech act badge when showSpeechAct=false', async () => {
    const el = await renderMessage({ showSpeechAct: false });
    const badge = el.shadowRoot!.querySelector('.speech-act-badge');
    expect(badge).toBeNull();
  });

  it('renders actor icon for AGENT type', async () => {
    const el = await renderMessage({ message: { actorType: 'AGENT' } });
    const icon = el.shadowRoot!.querySelector('.actor-icon');
    expect(icon).toBeTruthy();
    expect(icon!.getAttribute('data-actor')).toBe('AGENT');
  });

  it('renders actor icon for HUMAN type', async () => {
    const el = await renderMessage({ message: { actorType: 'HUMAN' } });
    const icon = el.shadowRoot!.querySelector('.actor-icon');
    expect(icon!.getAttribute('data-actor')).toBe('HUMAN');
  });

  it('renders actor icon for SYSTEM type', async () => {
    const el = await renderMessage({ message: { actorType: 'SYSTEM' } });
    const icon = el.shadowRoot!.querySelector('.actor-icon');
    expect(icon).toBeTruthy();
    expect(icon!.getAttribute('data-actor')).toBe('SYSTEM');
  });

  it('hides actor icon when showActorBadge=false', async () => {
    const el = await renderMessage({ showActorBadge: false });
    const icon = el.shadowRoot!.querySelector('.actor-icon');
    expect(icon).toBeNull();
  });

  it('renders commitment state badge for COMMAND messages', async () => {
    const el = await renderMessage({
      message: { messageType: 'COMMAND', commitmentId: 'c-1' },
    });
    el.commitmentState = 'OPEN';
    await (el as any).updateComplete;
    const badge = el.shadowRoot!.querySelector('.commitment-badge');
    expect(badge).toBeTruthy();
    expect(badge!.textContent!.trim()).toBe('OPEN');
  });

  it('applies correct CSS class for each commitment state', async () => {
    for (const [state, expected] of [
      ['OPEN', 'commitment-active'],
      ['ACKNOWLEDGED', 'commitment-info'],
      ['FULFILLED', 'commitment-success'],
      ['FAILED', 'commitment-danger'],
      ['DECLINED', 'commitment-neutral'],
      ['DELEGATED', 'commitment-transfer'],
      ['EXPIRED', 'commitment-warning'],
    ] as const) {
      const el = await renderMessage({
        message: { messageType: 'COMMAND', commitmentId: 'c-1' },
      });
      el.commitmentState = state;
      await (el as any).updateComplete;
      const badge = el.shadowRoot!.querySelector('.commitment-badge');
      expect(badge!.classList.contains(expected),
        `${state} should have ${expected}`).toBe(true);
      document.body.innerHTML = '';
    }
  });

  it('renders relative timestamp', async () => {
    const el = await renderMessage();
    const time = el.shadowRoot!.querySelector('time');
    expect(time).toBeTruthy();
    expect(time!.getAttribute('datetime')).toBe('2026-07-07T12:00:00Z');
  });

  it('applies correct badge color class for each message type', async () => {
    for (const [type, expected] of [
      ['COMMAND', 'obligation'],
      ['DONE', 'success'],
      ['FAILURE', 'danger'],
      ['DECLINE', 'warning'],
      ['HANDOFF', 'transfer'],
      ['EVENT', 'telemetry'],
      ['QUERY', 'info'],
      ['RESPONSE', 'info'],
      ['STATUS', 'info'],
    ] as const) {
      const el = await renderMessage({ message: { messageType: type } });
      const badge = el.shadowRoot!.querySelector('.speech-act-badge');
      expect(badge!.classList.contains(`badge-${expected}`),
        `${type} should have badge-${expected}`).toBe(true);
      document.body.innerHTML = '';
    }
  });

  it('renders delegation indicator for HANDOFF messages', async () => {
    const el = await renderMessage({
      message: { messageType: 'HANDOFF', target: 'agent-beta', sender: 'agent-alpha' },
    });
    const delegation = el.shadowRoot!.querySelector('.delegation-indicator');
    expect(delegation).toBeTruthy();
    expect(delegation!.textContent).toContain('agent-beta');
  });

  it('renders artefact chips when artefactRefs present', async () => {
    const el = await renderMessage({
      message: {
        artefactRefs: [
          { uri: 'doc:spec.md', type: 'DOCUMENT', label: 'Design Spec' },
        ],
      },
    });
    const chip = el.shadowRoot!.querySelector('.artefact-chip');
    expect(chip).toBeTruthy();
    expect(chip!.textContent).toContain('Design Spec');
  });

  it('renders nothing when message is not set', async () => {
    const el = document.createElement('qhorus-message') as any;
    document.body.appendChild(el);
    await el.updateComplete;
    const content = el.shadowRoot!.querySelector('.message-header');
    expect(content).toBeNull();
  });

  it('renders qhorus-reaction-bar when reactions array is set', async () => {
    const reactions = [
      { messageId: 'msg-1', emoji: '👍', actorId: 'a', createdAt: '2026-07-07T12:00:00Z' },
    ];
    const el = await renderMessage({ reactions });
    const reactionBar = el.shadowRoot!.querySelector('qhorus-reaction-bar');
    expect(reactionBar).toBeTruthy();
  });

  it('does not render reaction bar when reactions is empty', async () => {
    const el = await renderMessage({ reactions: [] });
    const reactionBar = el.shadowRoot!.querySelector('qhorus-reaction-bar');
    expect(reactionBar).toBeNull();
  });

  it('hides commitment badge when commitmentState is undefined', async () => {
    const el = await renderMessage({
      message: { messageType: 'COMMAND', commitmentId: 'c-1' },
    });
    const badge = el.shadowRoot!.querySelector('.commitment-badge');
    expect(badge).toBeNull();
  });

  it('hides commitment badge for non-COMMAND message types', async () => {
    const el = await renderMessage({
      message: { messageType: 'EVENT', commitmentId: 'c-1' },
    });
    el.commitmentState = 'OPEN';
    await (el as any).updateComplete;
    const badge = el.shadowRoot!.querySelector('.commitment-badge');
    expect(badge).toBeNull();
  });

  it('does not render delegation indicator for HANDOFF without target', async () => {
    const el = await renderMessage({
      message: { messageType: 'HANDOFF', sender: 'agent-alpha' },
    });
    const delegation = el.shadowRoot!.querySelector('.delegation-indicator');
    expect(delegation).toBeNull();
  });

  it('does not render delegation indicator for non-HANDOFF with target', async () => {
    const el = await renderMessage({
      message: { messageType: 'COMMAND', target: 'agent-beta' },
    });
    const delegation = el.shadowRoot!.querySelector('.delegation-indicator');
    expect(delegation).toBeNull();
  });

  it('renders multiple artefact chips', async () => {
    const el = await renderMessage({
      message: {
        artefactRefs: [
          { uri: 'doc:1', type: 'DOCUMENT', label: 'Spec 1' },
          { uri: 'doc:2', type: 'CODE', label: 'Code 2' },
          { uri: 'doc:3', type: 'CASE', label: 'Case 3' },
        ],
      },
    });
    const chips = el.shadowRoot!.querySelectorAll('.artefact-chip');
    expect(chips.length).toBe(3);
  });

  it('does not render artefact-chips container when artefactRefs is empty', async () => {
    const el = await renderMessage({
      message: { artefactRefs: [] },
    });
    const container = el.shadowRoot!.querySelector('.artefact-chips');
    expect(container).toBeNull();
  });

  it('_formatTime shows "now" for < 1 minute ago', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-07T12:00:30Z'));
    const el = await renderMessage({
      message: { createdAt: '2026-07-07T12:00:00Z' },
    });
    const time = el.shadowRoot!.querySelector('time');
    expect(time!.textContent).toBe('now');
    vi.useRealTimers();
  });

  it('_formatTime shows minutes for < 1 hour ago', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-07T12:05:00Z'));
    const el = await renderMessage({
      message: { createdAt: '2026-07-07T12:00:00Z' },
    });
    const time = el.shadowRoot!.querySelector('time');
    expect(time!.textContent).toBe('5m');
    vi.useRealTimers();
  });

  it('_formatTime shows hours for < 24 hours ago', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-07T15:00:00Z'));
    const el = await renderMessage({
      message: { createdAt: '2026-07-07T12:00:00Z' },
    });
    const time = el.shadowRoot!.querySelector('time');
    expect(time!.textContent).toBe('3h');
    vi.useRealTimers();
  });

  it('_formatTime shows days for >= 24 hours ago', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-09T12:00:00Z'));
    const el = await renderMessage({
      message: { createdAt: '2026-07-07T12:00:00Z' },
    });
    const time = el.shadowRoot!.querySelector('time');
    expect(time!.textContent).toBe('2d');
    vi.useRealTimers();
  });
});
