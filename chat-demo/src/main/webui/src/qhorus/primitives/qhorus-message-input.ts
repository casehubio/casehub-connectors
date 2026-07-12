import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state, query } from 'lit/decorators.js';
import { emitChatEvent, ChatEventTopics } from '../events.js';

@customElement('qhorus-message-input')
export class QhorusMessageInputElement extends LitElement {
  @property({ type: String }) channelId = '';
  @property({ type: Object }) replyTo?: { messageId: string; senderName: string };

  @state() private _text = '';

  @query('textarea') private _textarea!: HTMLTextAreaElement;

  static override readonly styles = css`
    :host {
      display: block;
      padding: var(--pages-space-2, 8px) var(--pages-space-4, 16px);
      border-top: 1px solid var(--pages-neutral-4, #e5e5e5);
    }
    .reply-banner {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: var(--pages-space-1, 4px) var(--pages-space-2, 8px);
      background: var(--pages-accent-2, #eef2ff);
      border-radius: var(--pages-radius-sm, 4px);
      margin-bottom: var(--pages-space-2, 8px);
      font-size: var(--pages-font-size-xs, 11px);
      color: var(--pages-accent-11, #3730a3);
    }
    .reply-cancel {
      cursor: pointer;
      background: none;
      border: none;
      color: var(--pages-neutral-8, #888);
      font-size: 14px;
    }
    textarea {
      width: 100%;
      resize: none;
      border: 1px solid var(--pages-neutral-5, #d4d4d4);
      border-radius: var(--pages-radius-md, 6px);
      padding: var(--pages-space-2, 8px) var(--pages-space-3, 12px);
      font-family: var(--pages-font-family, 'Inter', system-ui, sans-serif);
      font-size: var(--pages-font-size-base, 14px);
      line-height: var(--pages-line-height-base, 20px);
      color: var(--pages-neutral-12, #111);
      background: var(--pages-neutral-1, #fafafa);
      min-height: 40px;
      max-height: 200px;
      overflow-y: auto;
      box-sizing: border-box;
    }
    textarea:focus {
      outline: none;
      border-color: var(--pages-accent-7, #818cf8);
      box-shadow: 0 0 0 2px var(--pages-accent-3, #e0e7ff);
    }
  `;

  private _handleKeydown(e: KeyboardEvent) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      this._send();
    }
  }

  private _handleInput() {
    this._text = this._textarea.value;
    this._autoResize();
  }

  private _autoResize() {
    const ta = this._textarea;
    ta.style.height = 'auto';
    ta.style.height = `${Math.min(ta.scrollHeight, 200)}px`;
  }

  private _send() {
    const content = this._text.trim();
    if (!content || !this.channelId) return;

    emitChatEvent(this, ChatEventTopics.SEND_MESSAGE, {
      channelId: this.channelId,
      content,
      ...(this.replyTo ? { inReplyTo: this.replyTo.messageId } : {}),
    });

    this._text = '';
    this._textarea.value = '';
    this._textarea.style.height = 'auto';
    this.replyTo = undefined;
  }

  private _cancelReply() {
    this.replyTo = undefined;
  }

  override render() {
    return html`
      ${this.replyTo ? html`
        <div class="reply-banner">
          <span>Replying to <strong>${this.replyTo.senderName}</strong></span>
          <button class="reply-cancel" @click=${this._cancelReply}>✕</button>
        </div>
      ` : nothing}
      <textarea
        placeholder="Type a message..."
        @keydown=${this._handleKeydown}
        @input=${this._handleInput}
        rows="1"
      ></textarea>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'qhorus-message-input': QhorusMessageInputElement;
  }
}
