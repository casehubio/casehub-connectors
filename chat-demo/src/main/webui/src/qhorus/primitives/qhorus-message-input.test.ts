import { describe, it, expect, vi, afterEach } from 'vitest';
import './qhorus-message-input.js';

afterEach(() => { document.body.innerHTML = ''; });

describe('qhorus-message-input', () => {
  it('renders a textarea', async () => {
    const el = document.createElement('qhorus-message-input') as any;
    el.channelId = 'ch-1';
    document.body.appendChild(el);
    await el.updateComplete;
    expect(el.shadowRoot!.querySelector('textarea')).toBeTruthy();
  });

  it('emits chat:send-message on Enter', async () => {
    const el = document.createElement('qhorus-message-input') as any;
    el.channelId = 'ch-1';
    document.body.appendChild(el);
    await el.updateComplete;

    const handler = vi.fn();
    el.addEventListener('pages-event', handler);
    const textarea = el.shadowRoot!.querySelector('textarea')!;
    textarea.value = 'hello world';
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

    expect(handler).toHaveBeenCalledOnce();
    const detail = handler.mock.calls[0][0].detail;
    expect(detail.topic).toBe('chat:send-message');
    expect(detail.payload.content).toBe('hello world');
    expect(detail.payload.channelId).toBe('ch-1');
  });

  it('does not emit on Shift+Enter (newline)', async () => {
    const el = document.createElement('qhorus-message-input') as any;
    el.channelId = 'ch-1';
    document.body.appendChild(el);
    await el.updateComplete;

    const handler = vi.fn();
    el.addEventListener('pages-event', handler);
    const textarea = el.shadowRoot!.querySelector('textarea')!;
    textarea.value = 'line one';
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true }));

    expect(handler).not.toHaveBeenCalled();
  });

  it('does not emit empty messages', async () => {
    const el = document.createElement('qhorus-message-input') as any;
    el.channelId = 'ch-1';
    document.body.appendChild(el);
    await el.updateComplete;

    const handler = vi.fn();
    el.addEventListener('pages-event', handler);
    const textarea = el.shadowRoot!.querySelector('textarea')!;
    textarea.value = '   ';
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

    expect(handler).not.toHaveBeenCalled();
  });

  it('clears textarea after sending', async () => {
    const el = document.createElement('qhorus-message-input') as any;
    el.channelId = 'ch-1';
    document.body.appendChild(el);
    await el.updateComplete;

    const textarea = el.shadowRoot!.querySelector('textarea')!;
    textarea.value = 'hello';
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await el.updateComplete;

    expect(textarea.value).toBe('');
  });

  it('shows reply banner when replyTo is set', async () => {
    const el = document.createElement('qhorus-message-input') as any;
    el.channelId = 'ch-1';
    el.replyTo = { messageId: 'msg-1', senderName: 'agent-alpha' };
    document.body.appendChild(el);
    await el.updateComplete;

    const banner = el.shadowRoot!.querySelector('.reply-banner');
    expect(banner).toBeTruthy();
    expect(banner!.textContent).toContain('agent-alpha');
  });

  it('includes inReplyTo in sent message when replying', async () => {
    const el = document.createElement('qhorus-message-input') as any;
    el.channelId = 'ch-1';
    el.replyTo = { messageId: 'msg-1', senderName: 'alpha' };
    document.body.appendChild(el);
    await el.updateComplete;

    const handler = vi.fn();
    el.addEventListener('pages-event', handler);
    const textarea = el.shadowRoot!.querySelector('textarea')!;
    textarea.value = 'reply text';
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

    expect(handler.mock.calls[0][0].detail.payload.inReplyTo).toBe('msg-1');
  });

  it('does not emit event when channelId is empty string', async () => {
    const el = document.createElement('qhorus-message-input') as any;
    el.channelId = '';
    document.body.appendChild(el);
    await el.updateComplete;

    const handler = vi.fn();
    el.addEventListener('pages-event', handler);
    const textarea = el.shadowRoot!.querySelector('textarea')!;
    textarea.value = 'hello';
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

    expect(handler).not.toHaveBeenCalled();
  });

  it('clears replyTo banner when clicking cancel button', async () => {
    const el = document.createElement('qhorus-message-input') as any;
    el.channelId = 'ch-1';
    el.replyTo = { messageId: 'msg-1', senderName: 'alpha' };
    document.body.appendChild(el);
    await el.updateComplete;

    const cancelButton = el.shadowRoot!.querySelector('.reply-cancel')!;
    cancelButton.click();
    await el.updateComplete;

    expect(el.replyTo).toBeUndefined();
    const banner = el.shadowRoot!.querySelector('.reply-banner');
    expect(banner).toBeNull();
  });

  it('clears replyTo after sending message', async () => {
    const el = document.createElement('qhorus-message-input') as any;
    el.channelId = 'ch-1';
    el.replyTo = { messageId: 'msg-1', senderName: 'alpha' };
    document.body.appendChild(el);
    await el.updateComplete;

    const textarea = el.shadowRoot!.querySelector('textarea')!;
    textarea.value = 'reply text';
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await el.updateComplete;

    expect(el.replyTo).toBeUndefined();
  });

  it('textarea has aria-label', async () => {
    const el = document.createElement('qhorus-message-input') as any;
    el.channelId = 'ch-1';
    document.body.appendChild(el);
    await el.updateComplete;

    const textarea = el.shadowRoot!.querySelector('textarea')!;
    expect(textarea.getAttribute('aria-label')).toBe('Message');
  });

  it('cancel reply button has aria-label', async () => {
    const el = document.createElement('qhorus-message-input') as any;
    el.channelId = 'ch-1';
    el.replyTo = { messageId: 'msg-1', senderName: 'agent-alpha' };
    document.body.appendChild(el);
    await el.updateComplete;

    const cancelButton = el.shadowRoot!.querySelector('.reply-cancel')!;
    expect(cancelButton.getAttribute('aria-label')).toBe('Cancel reply');
  });

  it('trims content before sending', async () => {
    const el = document.createElement('qhorus-message-input') as any;
    el.channelId = 'ch-1';
    document.body.appendChild(el);
    await el.updateComplete;

    const handler = vi.fn();
    el.addEventListener('pages-event', handler);
    const textarea = el.shadowRoot!.querySelector('textarea')!;
    textarea.value = '  hello  ';
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

    expect(handler.mock.calls[0][0].detail.payload.content).toBe('hello');
  });
});
