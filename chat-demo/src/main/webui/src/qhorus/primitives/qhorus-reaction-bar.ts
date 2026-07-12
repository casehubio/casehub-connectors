// chat-demo/src/main/webui/src/qhorus/primitives/qhorus-reaction-bar.ts
import { LitElement, html, css, nothing } from 'lit';
import { customElement, property } from 'lit/decorators.js';
import type { Reaction } from '../types.js';
import { emitChatEvent, ChatEventTopics } from '../events.js';

interface GroupedReaction {
  readonly emoji: string;
  readonly count: number;
  readonly actors: readonly string[];
  readonly userReacted: boolean;
}

@customElement('qhorus-reaction-bar')
export class QhorusReactionBarElement extends LitElement {
  @property({ type: Array }) reactions: Reaction[] = [];
  @property({ type: String }) messageId = '';
  @property({ type: String }) currentActorId?: string;

  static override readonly styles = css`
    :host { display: flex; gap: var(--pages-space-1, 4px); flex-wrap: wrap; margin-top: var(--pages-space-1, 4px); }
    .reaction-pill {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      padding: 2px 8px;
      border-radius: 9999px;
      border: 1px solid var(--pages-neutral-5, #d4d4d4);
      background: var(--pages-neutral-1, #fafafa);
      font-size: var(--pages-font-size-xs, 11px);
      cursor: pointer;
      user-select: none;
    }
    .reaction-pill:hover { background: var(--pages-neutral-3, #e5e5e5); }
    .reaction-pill.reacted {
      border-color: var(--pages-accent-7, #818cf8);
      background: var(--pages-accent-2, #eef2ff);
    }
    .count { color: var(--pages-neutral-9, #737373); }
  `;

  private _grouped(): GroupedReaction[] {
    const map = new Map<string, { actors: string[] }>();
    for (const r of this.reactions) {
      const entry = map.get(r.emoji) ?? { actors: [] };
      entry.actors.push(r.actorId);
      map.set(r.emoji, entry);
    }
    return [...map.entries()].map(([emoji, { actors }]) => ({
      emoji,
      count: actors.length,
      actors,
      userReacted: this.currentActorId != null && actors.includes(this.currentActorId),
    }));
  }

  private _toggleReaction(emoji: string, userReacted: boolean) {
    const topic = userReacted ? ChatEventTopics.UNREACT : ChatEventTopics.REACT;
    emitChatEvent(this, topic, { messageId: this.messageId, emoji });
  }

  override render() {
    const groups = this._grouped();
    if (groups.length === 0) return nothing;
    return html`${groups.map(g => html`
      <button class="reaction-pill ${g.userReacted ? 'reacted' : ''}"
              @click=${() => this._toggleReaction(g.emoji, g.userReacted)}>
        <span class="emoji">${g.emoji}</span>
        <span class="count">${g.count}</span>
      </button>
    `)}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'qhorus-reaction-bar': QhorusReactionBarElement;
  }
}
