import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import type { QhorusMessage, Reaction, CommitmentState } from '../types.js';
import '../primitives/qhorus-message.js';
import '../primitives/qhorus-thread.js';

interface MessageGroup {
  sender: string;
  actorType: string;
  messages: QhorusMessage[];
}

@customElement('qhorus-channel-feed')
export class QhorusChannelFeedElement extends LitElement {
  @property({ type: Array }) messages: QhorusMessage[] = [];
  @property({ type: Array }) reactions: Reaction[] = [];
  @property({ type: Object }) commitments: Map<string, CommitmentState> = new Map();
  @property({ type: String }) channelName?: string;

  @state() private _prevMessageCount = 0;

  static override readonly styles = css`
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
      overflow: hidden;
    }
    .feed {
      flex: 1;
      overflow-y: auto;
      scroll-behavior: smooth;
    }
    @media (prefers-reduced-motion: reduce) {
      .feed { scroll-behavior: auto; }
    }
    .message-group-header {
      display: flex;
      align-items: center;
      gap: var(--pages-space-2, 8px);
      padding: var(--pages-space-3, 12px) var(--pages-space-4, 16px) 0;
    }
    .group-sender {
      font-weight: var(--pages-font-weight-semibold, 600);
      font-size: var(--pages-font-size-sm, 13px);
    }
    .message-item { }
    .empty {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 100%;
      color: var(--pages-neutral-8, #888);
      font-size: var(--pages-font-size-sm, 13px);
    }
  `;

  private _reactionsFor(messageId: string): Reaction[] {
    return this.reactions.filter(r => r.messageId === messageId);
  }

  _separateRootsAndReplies(): {
    roots: QhorusMessage[];
    repliesByParent: Map<string, QhorusMessage[]>;
  } {
    const repliesByParent = new Map<string, QhorusMessage[]>();
    const roots: QhorusMessage[] = [];

    for (const m of this.messages) {
      if (m.inReplyTo) {
        const list = repliesByParent.get(m.inReplyTo) ?? [];
        list.push(m);
        repliesByParent.set(m.inReplyTo, list);
      } else {
        roots.push(m);
      }
    }
    return { roots, repliesByParent };
  }

  private _groupFlat(messages: QhorusMessage[]): MessageGroup[] {
    const groups: MessageGroup[] = [];
    const TWO_MINUTES = 2 * 60 * 1000;

    for (const msg of messages) {
      const last = groups[groups.length - 1];
      if (last && last.sender === msg.sender) {
        const lastTime = new Date(last.messages[last.messages.length - 1].createdAt).getTime();
        const thisTime = new Date(msg.createdAt).getTime();
        if (thisTime - lastTime < TWO_MINUTES) {
          last.messages = [...last.messages, msg];
          continue;
        }
      }
      groups.push({ sender: msg.sender, actorType: msg.actorType, messages: [msg] });
    }
    return groups;
  }

  override updated(changed: Map<string, unknown>) {
    if (changed.has('messages') && this.messages.length > this._prevMessageCount) {
      this._prevMessageCount = this.messages.length;
    }
  }

  override render() {
    return html`
      <div class="feed">
        ${this.messages.length === 0 ? html`
          <div class="empty">No messages yet</div>
        ` : this._renderFeed()}
      </div>
    `;
  }

  private _renderFeed() {
    const { roots, repliesByParent } = this._separateRootsAndReplies();
    return this._groupFlat(roots).map(group => html`
      <div class="message-group">
        <div class="message-group-header">
          <span class="group-sender">${group.sender}</span>
        </div>
        ${group.messages.map(msg => html`
          <div class="message-item">
            <qhorus-message .message=${msg}
                            .reactions=${this._reactionsFor(msg.id)}
                            .showActorBadge=${group.messages.indexOf(msg) === 0}
                            .channelName=${this.channelName}
                            .parentMessage=${msg.inReplyTo ? this.messages.find(m => m.id === msg.inReplyTo) : undefined}>
            </qhorus-message>
          </div>
          ${repliesByParent.has(msg.id) ? html`
            <qhorus-thread .rootMessage=${msg}
                           .replies=${repliesByParent.get(msg.id)!}>
            </qhorus-thread>
          ` : nothing}
        `)}
      </div>
    `);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'qhorus-channel-feed': QhorusChannelFeedElement;
  }
}
