// chat-demo/src/main/webui/src/qhorus/composites/qhorus-channel-nav.ts
import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import type { QhorusChannel, ChannelSemantic } from '../types.js';
import { emitChatEvent, ChatEventTopics } from '../events.js';

@customElement('qhorus-channel-nav')
export class QhorusChannelNav extends LitElement {
  @property({ type: Array }) channels: QhorusChannel[] = [];
  @property({ type: String }) selectedChannelId?: string;
  @state() private _focusedIndex = 0;

  static override readonly styles = css`
    :host {
      display: block;
      padding: var(--pages-space-3, 12px);
      background: var(--pages-neutral-1, #ffffff);
      color: var(--pages-neutral-12, #1a1a1a);
      height: 100%;
      box-sizing: border-box;
      overflow-y: auto;
    }

    .channel-list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: var(--pages-space-1, 4px);
    }

    .channel-item {
      display: flex;
      align-items: center;
      gap: var(--pages-space-2, 8px);
      padding: var(--pages-space-2, 8px);
      border-radius: var(--pages-radius-1, 4px);
      cursor: pointer;
      transition: background 0.2s;
      position: relative;
    }

    .channel-item:hover {
      background: var(--pages-neutral-3, #f5f5f5);
    }

    .channel-item.selected {
      background: var(--pages-accent-3, #e0f2fe);
    }

    .channel-item.focused {
      outline: 2px solid var(--pages-accent-7, #818cf8);
      outline-offset: -2px;
    }

    .channel-icon {
      flex-shrink: 0;
      font-size: 14px;
      color: var(--pages-neutral-9, #999);
      margin-right: 2px;
    }

    .channel-name {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .delete-btn {
      flex-shrink: 0;
      background: transparent;
      border: none;
      color: var(--pages-neutral-8, #6b7280);
      cursor: pointer;
      padding: var(--pages-space-1, 4px);
      border-radius: var(--pages-radius-1, 4px);
      font-size: 14px;
      line-height: 1;
      opacity: 0;
      transition: opacity 0.2s, background 0.2s;
    }

    .channel-item:hover .delete-btn {
      opacity: 1;
    }

    .delete-btn:hover {
      background: var(--pages-neutral-4, #e5e5e5);
      color: var(--pages-danger-1, #dc2626);
    }

    .create-channel-btn {
      margin-top: var(--pages-space-3, 12px);
      width: 100%;
      padding: var(--pages-space-2, 8px);
      background: var(--pages-accent-1, #0ea5e9);
      color: #fff;
      border: none;
      border-radius: var(--pages-radius-1, 4px);
      cursor: pointer;
      font-size: 14px;
      font-weight: 500;
      transition: background 0.2s;
    }

    .create-channel-btn:hover {
      background: var(--pages-accent-2, #0284c7);
    }
  `;

  private getChannelIcon(_semantic: ChannelSemantic): string {
    return '#';
  }

  private handleChannelClick(channelId: string): void {
    emitChatEvent(this, ChatEventTopics.SELECT_CHANNEL, { channelId });
  }

  private handleDeleteClick(event: MouseEvent, channel: QhorusChannel): void {
    event.stopPropagation(); // Prevent channel selection
    const confirmed = window.confirm(`Delete channel "${channel.name}"?`);
    if (confirmed) {
      emitChatEvent(this, ChatEventTopics.DELETE_CHANNEL, { channelId: channel.id });
    }
  }

  private handleCreateChannel(): void {
    const name = window.prompt('Enter channel name:');
    if (name && name.trim()) {
      emitChatEvent(this, ChatEventTopics.CREATE_CHANNEL, { name: name.trim() });
    }
  }

  private handleKeyDown(event: KeyboardEvent): void {
    if (this.channels.length === 0) return;

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this._focusedIndex = Math.min(this._focusedIndex + 1, this.channels.length - 1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this._focusedIndex = Math.max(this._focusedIndex - 1, 0);
        break;
      case 'Enter':
        event.preventDefault();
        if (this.channels[this._focusedIndex]) {
          this.handleChannelClick(this.channels[this._focusedIndex].id);
        }
        break;
    }
  }

  override render() {
    return html`
      <ul class="channel-list" role="list" tabindex="0" @keydown="${this.handleKeyDown}">
        ${this.channels.map(
          (channel, index) => html`
            <li
              class="channel-item ${this.selectedChannelId === channel.id ? 'selected' : ''} ${index === this._focusedIndex ? 'focused' : ''}"
              role="option"
              aria-selected="${this.selectedChannelId === channel.id}"
              @click="${() => this.handleChannelClick(channel.id)}"
            >
              <span class="channel-icon">${this.getChannelIcon(channel.semantic)}</span>
              <span class="channel-name">${channel.name}</span>
              <button
                class="delete-btn"
                aria-label="Delete channel ${channel.name}"
                @click="${(e: MouseEvent) => this.handleDeleteClick(e, channel)}"
              >
                ✕
              </button>
            </li>
          `
        )}
      </ul>
      <button class="create-channel-btn" @click="${this.handleCreateChannel}">
        Create Channel
      </button>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'qhorus-channel-nav': QhorusChannelNav;
  }
}
