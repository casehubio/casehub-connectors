import { LitElement, html, css } from 'lit';
import { customElement, property } from 'lit/decorators.js';
import 'emoji-picker-element';

@customElement('qhorus-emoji-picker')
export class QhorusEmojiPickerElement extends LitElement {
  @property({ type: String }) skinToneEmoji?: string;
  static override readonly styles = css`
    :host {
      display: block;
    }
    emoji-picker {
      --background: var(--pages-neutral-1, #fff);
      --border-color: var(--pages-neutral-5, #d4d4d4);
      --text-color: var(--pages-neutral-12, #111);
      --secondary-text-color: var(--pages-neutral-8, #888);
      --indicator-color: var(--pages-accent-9, #6366f1);
      --input-border-color: var(--pages-neutral-5, #d4d4d4);
      --button-hover-background: var(--pages-neutral-3, #e5e5e5);
      --button-active-background: var(--pages-neutral-4, #d4d4d4);
      --category-font-size: var(--pages-font-size-xs, 11px);
      --font-family: var(--pages-font-family, 'Inter', system-ui, sans-serif);
      --font-size: var(--pages-font-size-base, 14px);
    }
  `;

  private _onEmojiClick = (e: Event) => {
    const detail = (e as CustomEvent).detail;
    const unicode = detail.unicode ?? detail.emoji?.unicode;
    if (!unicode) return;
    this.dispatchEvent(new CustomEvent('emoji-selected', {
      bubbles: true,
      composed: true,
      detail: { emoji: unicode },
    }));
  };

  override render() {
    return html`<emoji-picker
      skin-tone-emoji=${this.skinToneEmoji ?? '🖐️'}
      @emoji-click=${this._onEmojiClick}></emoji-picker>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'qhorus-emoji-picker': QhorusEmojiPickerElement;
  }
}
