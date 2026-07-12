// chat-demo/src/main/webui/src/qhorus/composites/qhorus-channel-nav.test.ts
import { describe, it, expect, afterEach, vi, beforeEach } from 'vitest';
import './qhorus-channel-nav.js';
import type { QhorusChannel } from '../types.js';

describe('qhorus-channel-nav', () => {
  let el: HTMLElement;

  afterEach(() => {
    el?.remove();
    vi.restoreAllMocks();
  });

  it('renders list of channels with names', async () => {
    el = document.createElement('qhorus-channel-nav');
    const channels: QhorusChannel[] = [
      { id: 'ch1', name: 'General', semantic: 'APPEND', paused: false },
      { id: 'ch2', name: 'Urgent', semantic: 'COLLECT', paused: false },
    ];
    (el as any).channels = channels;
    document.body.appendChild(el);
    await el.updateComplete;

    const items = el.shadowRoot!.querySelectorAll('.channel-item');
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('General');
    expect(items[1].textContent).toContain('Urgent');
  });

  it('highlights selected channel with active class', async () => {
    el = document.createElement('qhorus-channel-nav');
    const channels: QhorusChannel[] = [
      { id: 'ch1', name: 'General', semantic: 'APPEND', paused: false },
      { id: 'ch2', name: 'Urgent', semantic: 'COLLECT', paused: false },
    ];
    (el as any).channels = channels;
    (el as any).selectedChannelId = 'ch1';
    document.body.appendChild(el);
    await el.updateComplete;

    const items = el.shadowRoot!.querySelectorAll('.channel-item');
    expect(items[0].classList.contains('selected')).toBe(true);
    expect(items[1].classList.contains('selected')).toBe(false);
    expect(items[0].getAttribute('aria-selected')).toBe('true');
    expect(items[1].getAttribute('aria-selected')).toBe('false');
  });

  it('emits chat:select-channel on channel click', async () => {
    el = document.createElement('qhorus-channel-nav');
    const channels: QhorusChannel[] = [
      { id: 'ch1', name: 'General', semantic: 'APPEND', paused: false },
    ];
    (el as any).channels = channels;
    document.body.appendChild(el);
    await el.updateComplete;

    const listener = vi.fn();
    el.addEventListener('pages-event', listener);

    const item = el.shadowRoot!.querySelector('.channel-item') as HTMLElement;
    item.click();

    expect(listener).toHaveBeenCalledTimes(1);
    const event = listener.mock.calls[0][0] as CustomEvent;
    expect(event.detail.topic).toBe('chat:select-channel');
    expect(event.detail.payload).toEqual({ channelId: 'ch1' });
  });

  it('renders create channel button', async () => {
    el = document.createElement('qhorus-channel-nav');
    document.body.appendChild(el);
    await el.updateComplete;

    const createBtn = el.shadowRoot!.querySelector('.create-channel-btn');
    expect(createBtn).toBeTruthy();
    expect(createBtn!.textContent).toContain('Create Channel');
  });

  it('emits chat:create-channel with name from prompt', async () => {
    el = document.createElement('qhorus-channel-nav');
    document.body.appendChild(el);
    await el.updateComplete;

    const promptSpy = vi.spyOn(window, 'prompt').mockReturnValue('New Channel');
    const listener = vi.fn();
    el.addEventListener('pages-event', listener);

    const createBtn = el.shadowRoot!.querySelector('.create-channel-btn') as HTMLElement;
    createBtn.click();

    expect(promptSpy).toHaveBeenCalledWith('Enter channel name:');
    expect(listener).toHaveBeenCalledTimes(1);
    const event = listener.mock.calls[0][0] as CustomEvent;
    expect(event.detail.topic).toBe('chat:create-channel');
    expect(event.detail.payload).toEqual({ name: 'New Channel' });
  });

  it('does not emit chat:create-channel when prompt is cancelled', async () => {
    el = document.createElement('qhorus-channel-nav');
    document.body.appendChild(el);
    await el.updateComplete;

    const promptSpy = vi.spyOn(window, 'prompt').mockReturnValue(null);
    const listener = vi.fn();
    el.addEventListener('pages-event', listener);

    const createBtn = el.shadowRoot!.querySelector('.create-channel-btn') as HTMLElement;
    createBtn.click();

    expect(promptSpy).toHaveBeenCalled();
    expect(listener).not.toHaveBeenCalled();
  });

  it('renders delete button per channel', async () => {
    el = document.createElement('qhorus-channel-nav');
    const channels: QhorusChannel[] = [
      { id: 'ch1', name: 'General', semantic: 'APPEND', paused: false },
      { id: 'ch2', name: 'Urgent', semantic: 'COLLECT', paused: false },
    ];
    (el as any).channels = channels;
    document.body.appendChild(el);
    await el.updateComplete;

    const deleteButtons = el.shadowRoot!.querySelectorAll('.delete-btn');
    expect(deleteButtons.length).toBe(2);
  });

  it('emits chat:delete-channel with channelId after confirm', async () => {
    el = document.createElement('qhorus-channel-nav');
    const channels: QhorusChannel[] = [
      { id: 'ch1', name: 'General', semantic: 'APPEND', paused: false },
    ];
    (el as any).channels = channels;
    document.body.appendChild(el);
    await el.updateComplete;

    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const listener = vi.fn();
    el.addEventListener('pages-event', listener);

    const deleteBtn = el.shadowRoot!.querySelector('.delete-btn') as HTMLElement;
    deleteBtn.click();

    expect(confirmSpy).toHaveBeenCalledWith('Delete channel "General"?');
    expect(listener).toHaveBeenCalledTimes(1);
    const event = listener.mock.calls[0][0] as CustomEvent;
    expect(event.detail.topic).toBe('chat:delete-channel');
    expect(event.detail.payload).toEqual({ channelId: 'ch1' });
  });

  it('does not emit chat:delete-channel when confirm is cancelled', async () => {
    el = document.createElement('qhorus-channel-nav');
    const channels: QhorusChannel[] = [
      { id: 'ch1', name: 'General', semantic: 'APPEND', paused: false },
    ];
    (el as any).channels = channels;
    document.body.appendChild(el);
    await el.updateComplete;

    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const listener = vi.fn();
    el.addEventListener('pages-event', listener);

    const deleteBtn = el.shadowRoot!.querySelector('.delete-btn') as HTMLElement;
    deleteBtn.click();

    expect(confirmSpy).toHaveBeenCalled();
    expect(listener).not.toHaveBeenCalled();
  });

  it('shows channel semantic icon', async () => {
    el = document.createElement('qhorus-channel-nav');
    const channels: QhorusChannel[] = [
      { id: 'ch1', name: 'General', semantic: 'APPEND', paused: false },
      { id: 'ch2', name: 'Collect', semantic: 'COLLECT', paused: false },
      { id: 'ch3', name: 'Barrier', semantic: 'BARRIER', paused: false },
      { id: 'ch4', name: 'Ephemeral', semantic: 'EPHEMERAL', paused: false },
      { id: 'ch5', name: 'LastWrite', semantic: 'LAST_WRITE', paused: false },
    ];
    (el as any).channels = channels;
    document.body.appendChild(el);
    await el.updateComplete;

    const items = el.shadowRoot!.querySelectorAll('.channel-item');
    for (const item of items) {
      expect(item.textContent).toContain('#');
    }
  });

  it('prevents delete button click from selecting channel', async () => {
    el = document.createElement('qhorus-channel-nav');
    const channels: QhorusChannel[] = [
      { id: 'ch1', name: 'General', semantic: 'APPEND', paused: false },
    ];
    (el as any).channels = channels;
    document.body.appendChild(el);
    await el.updateComplete;

    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const listener = vi.fn();
    el.addEventListener('pages-event', listener);

    const deleteBtn = el.shadowRoot!.querySelector('.delete-btn') as HTMLElement;
    deleteBtn.click();

    // Only confirm was called, no select-channel event
    expect(listener).not.toHaveBeenCalled();
  });

  it('navigates channels with arrow keys', async () => {
    el = document.createElement('qhorus-channel-nav');
    const channels: QhorusChannel[] = [
      { id: 'ch1', name: 'General', semantic: 'APPEND', paused: false },
      { id: 'ch2', name: 'Urgent', semantic: 'COLLECT', paused: false },
      { id: 'ch3', name: 'Random', semantic: 'BARRIER', paused: false },
    ];
    (el as any).channels = channels;
    document.body.appendChild(el);
    await el.updateComplete;

    const list = el.shadowRoot!.querySelector('.channel-list') as HTMLElement;
    let items = el.shadowRoot!.querySelectorAll('.channel-item');

    // Initially focused on first item
    expect(items[0].classList.contains('focused')).toBe(true);
    expect(items[1].classList.contains('focused')).toBe(false);
    expect(items[2].classList.contains('focused')).toBe(false);

    // ArrowDown moves to second item
    list.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    await el.updateComplete;
    items = el.shadowRoot!.querySelectorAll('.channel-item');
    expect(items[0].classList.contains('focused')).toBe(false);
    expect(items[1].classList.contains('focused')).toBe(true);
    expect(items[2].classList.contains('focused')).toBe(false);

    // ArrowDown moves to third item
    list.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    await el.updateComplete;
    items = el.shadowRoot!.querySelectorAll('.channel-item');
    expect(items[0].classList.contains('focused')).toBe(false);
    expect(items[1].classList.contains('focused')).toBe(false);
    expect(items[2].classList.contains('focused')).toBe(true);

    // ArrowDown at end stays on last item
    list.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    await el.updateComplete;
    items = el.shadowRoot!.querySelectorAll('.channel-item');
    expect(items[2].classList.contains('focused')).toBe(true);

    // ArrowUp moves back to second item
    list.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true }));
    await el.updateComplete;
    items = el.shadowRoot!.querySelectorAll('.channel-item');
    expect(items[0].classList.contains('focused')).toBe(false);
    expect(items[1].classList.contains('focused')).toBe(true);
    expect(items[2].classList.contains('focused')).toBe(false);

    // Enter selects the focused channel
    const listener = vi.fn();
    el.addEventListener('pages-event', listener);
    list.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));

    expect(listener).toHaveBeenCalledTimes(1);
    const event = listener.mock.calls[0][0] as CustomEvent;
    expect(event.detail.topic).toBe('chat:select-channel');
    expect(event.detail.payload).toEqual({ channelId: 'ch2' });
  });

  it('rejects whitespace-only channel names', async () => {
    el = document.createElement('qhorus-channel-nav');
    document.body.appendChild(el);
    await el.updateComplete;

    const promptSpy = vi.spyOn(window, 'prompt').mockReturnValue('   ');
    const listener = vi.fn();
    el.addEventListener('pages-event', listener);

    const createBtn = el.shadowRoot!.querySelector('.create-channel-btn') as HTMLElement;
    createBtn.click();

    expect(promptSpy).toHaveBeenCalled();
    expect(listener).not.toHaveBeenCalled();
  });

  it('updates highlight when selectedChannelId changes', async () => {
    el = document.createElement('qhorus-channel-nav');
    const channels: QhorusChannel[] = [
      { id: 'ch-1', name: 'General', semantic: 'APPEND', paused: false },
      { id: 'ch-2', name: 'Urgent', semantic: 'COLLECT', paused: false },
    ];
    (el as any).channels = channels;
    (el as any).selectedChannelId = 'ch-1';
    document.body.appendChild(el);
    await el.updateComplete;

    let items = el.shadowRoot!.querySelectorAll('.channel-item');
    expect(items[0].classList.contains('selected')).toBe(true);
    expect(items[1].classList.contains('selected')).toBe(false);

    (el as any).selectedChannelId = 'ch-2';
    await el.updateComplete;

    items = el.shadowRoot!.querySelectorAll('.channel-item');
    expect(items[0].classList.contains('selected')).toBe(false);
    expect(items[1].classList.contains('selected')).toBe(true);
  });

  it('re-renders when channels array changes', async () => {
    el = document.createElement('qhorus-channel-nav');
    const channels: QhorusChannel[] = [
      { id: 'ch1', name: 'General', semantic: 'APPEND', paused: false },
      { id: 'ch2', name: 'Urgent', semantic: 'COLLECT', paused: false },
    ];
    (el as any).channels = channels;
    document.body.appendChild(el);
    await el.updateComplete;

    let items = el.shadowRoot!.querySelectorAll('.channel-item');
    expect(items.length).toBe(2);

    (el as any).channels = [
      ...channels,
      { id: 'ch3', name: 'Random', semantic: 'BARRIER', paused: false },
    ];
    await el.updateComplete;

    items = el.shadowRoot!.querySelectorAll('.channel-item');
    expect(items.length).toBe(3);
  });

  it('keeps focus at top when ArrowUp pressed at first item', async () => {
    el = document.createElement('qhorus-channel-nav');
    const channels: QhorusChannel[] = [
      { id: 'ch1', name: 'General', semantic: 'APPEND', paused: false },
      { id: 'ch2', name: 'Urgent', semantic: 'COLLECT', paused: false },
    ];
    (el as any).channels = channels;
    document.body.appendChild(el);
    await el.updateComplete;

    const list = el.shadowRoot!.querySelector('.channel-list') as HTMLElement;
    let items = el.shadowRoot!.querySelectorAll('.channel-item');

    expect(items[0].classList.contains('focused')).toBe(true);

    list.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true }));
    await el.updateComplete;

    items = el.shadowRoot!.querySelectorAll('.channel-item');
    expect(items[0].classList.contains('focused')).toBe(true);
    expect((el as any)._focusedIndex).toBe(0);
  });

  it('handles empty channel list without crashing', async () => {
    el = document.createElement('qhorus-channel-nav');
    (el as any).channels = [];
    document.body.appendChild(el);
    await el.updateComplete;

    const items = el.shadowRoot!.querySelectorAll('.channel-item');
    expect(items.length).toBe(0);

    const list = el.shadowRoot!.querySelector('.channel-list') as HTMLElement;
    list.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    await el.updateComplete;

    expect((el as any)._focusedIndex).toBe(0);
  });
});
