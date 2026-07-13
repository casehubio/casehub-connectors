import { describe, it, expect, vi, afterEach } from 'vitest';
import './qhorus-emoji-picker.js';

afterEach(() => { document.body.innerHTML = ''; });

describe('qhorus-emoji-picker', () => {
  it('renders an emoji-picker element', async () => {
    const el = document.createElement('qhorus-emoji-picker') as any;
    document.body.appendChild(el);
    await el.updateComplete;
    const picker = el.shadowRoot!.querySelector('emoji-picker');
    expect(picker).toBeTruthy();
  });

  it('emits emoji-selected event on emoji click', async () => {
    const el = document.createElement('qhorus-emoji-picker') as any;
    document.body.appendChild(el);
    await el.updateComplete;

    const handler = vi.fn();
    el.addEventListener('emoji-selected', handler);

    const picker = el.shadowRoot!.querySelector('emoji-picker')!;
    picker.dispatchEvent(new CustomEvent('emoji-click', {
      detail: { unicode: '😀', emoji: { unicode: '😀' } },
      bubbles: true,
    }));

    expect(handler).toHaveBeenCalledOnce();
    expect(handler.mock.calls[0][0].detail.emoji).toBe('😀');
  });

  it('passes skinToneEmoji attribute to inner picker', async () => {
    const el = document.createElement('qhorus-emoji-picker') as any;
    el.skinToneEmoji = '👍';
    document.body.appendChild(el);
    await el.updateComplete;
    const picker = el.shadowRoot!.querySelector('emoji-picker') as any;
    expect(picker.getAttribute('skin-tone-emoji')).toBe('👍');
  });

  it('does not override dataSource (preserves IndexedDB favorites persistence)', async () => {
    const el = document.createElement('qhorus-emoji-picker') as any;
    document.body.appendChild(el);
    await el.updateComplete;
    const picker = el.shadowRoot!.querySelector('emoji-picker') as any;
    expect(picker.getAttribute('data-source')).toBeNull();
  });

  it('does not emit when emoji-click has no unicode', async () => {
    const el = document.createElement('qhorus-emoji-picker') as any;
    document.body.appendChild(el);
    await el.updateComplete;

    const handler = vi.fn();
    el.addEventListener('emoji-selected', handler);

    const picker = el.shadowRoot!.querySelector('emoji-picker')!;
    picker.dispatchEvent(new CustomEvent('emoji-click', {
      detail: {},
      bubbles: true,
    }));

    expect(handler).not.toHaveBeenCalled();
  });
});
