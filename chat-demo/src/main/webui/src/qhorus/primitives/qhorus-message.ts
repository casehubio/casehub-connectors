// chat-demo/src/main/webui/src/qhorus/primitives/qhorus-message.ts
import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { unsafeHTML } from 'lit/directives/unsafe-html.js';
import type { QhorusMessage, Reaction, CommitmentState } from '../types.js';
import { messageTypeCategory, commitmentStateCategory, isObligationCreating } from '../types.js';
import { renderMarkdown } from '../markdown.js';
import { emitChatEvent, ChatEventTopics } from '../events.js';

@customElement('qhorus-message')
export class QhorusMessageElement extends LitElement {
  @property({ type: Object }) message!: QhorusMessage;
  @property({ type: Array }) reactions: Reaction[] = [];
  @property({ type: Boolean }) showSpeechAct = true;
  @property({ type: Boolean }) showActorBadge = true;
  @property({ type: String }) commitmentState?: CommitmentState;
  @property({ type: Object }) parentMessage?: QhorusMessage;
  @property({ type: String }) channelName?: string;

  @state() private _expanded = false;

  static override readonly styles = css`
    :host {
      display: block;
      padding: var(--pages-space-2, 8px) var(--pages-space-4, 16px);
    }
    :host(:hover) {
      background: var(--pages-neutral-2, #f5f5f5);
    }
    .message-header {
      display: flex;
      align-items: center;
      gap: var(--pages-space-2, 8px);
      margin-bottom: var(--pages-space-1, 4px);
    }
    .actor-icon { display: none; }
    .sender {
      font-weight: var(--pages-font-weight-semibold, 600);
      font-size: var(--pages-font-size-sm, 13px);
      color: var(--pages-neutral-12, #111);
    }
    time {
      font-size: var(--pages-font-size-xs, 11px);
      color: var(--pages-neutral-8, #888);
    }
    .speech-act-badge {
      font-size: 10px;
      font-weight: var(--pages-font-weight-medium, 500);
      padding: 1px 6px;
      border-radius: 9999px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .badge-info { background: var(--pages-info-3, #dbeafe); color: var(--pages-info-11, #1e40af); }
    .badge-obligation { background: var(--pages-accent-3, #e0e7ff); color: var(--pages-accent-11, #3730a3); }
    .badge-success { background: var(--pages-success-3, #d1fae5); color: var(--pages-success-11, #065f46); }
    .badge-danger { background: var(--pages-danger-3, #fee2e2); color: var(--pages-danger-11, #991b1b); }
    .badge-warning { background: var(--pages-warning-3, #fef3c7); color: var(--pages-warning-11, #92400e); }
    .badge-transfer { background: var(--pages-info-3, #dbeafe); color: var(--pages-info-11, #1e40af); }
    .badge-telemetry { background: var(--pages-neutral-3, #e5e5e5); color: var(--pages-neutral-9, #737373); }
    .commitment-badge {
      font-size: 10px;
      padding: 1px 6px;
      border-radius: var(--pages-radius-sm, 4px);
    }
    .commitment-active { background: var(--pages-accent-3, #e0e7ff); color: var(--pages-accent-11, #3730a3); }
    .commitment-info { background: var(--pages-info-3, #dbeafe); color: var(--pages-info-11, #1e40af); }
    .commitment-success { background: var(--pages-success-3, #d1fae5); color: var(--pages-success-11, #065f46); }
    .commitment-danger { background: var(--pages-danger-3, #fee2e2); color: var(--pages-danger-11, #991b1b); }
    .commitment-neutral { background: var(--pages-neutral-3, #e5e5e5); color: var(--pages-neutral-9, #737373); }
    .commitment-transfer { background: var(--pages-info-3, #dbeafe); color: var(--pages-info-11, #1e40af); }
    .commitment-warning { background: var(--pages-warning-3, #fef3c7); color: var(--pages-warning-11, #92400e); }
    .content {
      font-size: var(--pages-font-size-base, 14px);
      line-height: var(--pages-line-height-base, 20px);
      color: var(--pages-neutral-11, #333);
    }
    .content :first-child { margin-top: 0; }
    .content :last-child { margin-bottom: 0; }
    .delegation-indicator {
      display: flex;
      align-items: center;
      gap: var(--pages-space-1, 4px);
      font-size: var(--pages-font-size-xs, 11px);
      color: var(--pages-info-9, #2563eb);
      margin-top: var(--pages-space-1, 4px);
    }
    .artefact-chip {
      display: inline-flex;
      align-items: center;
      gap: var(--pages-space-1, 4px);
      font-size: var(--pages-font-size-xs, 11px);
      padding: 2px 8px;
      border-radius: var(--pages-radius-sm, 4px);
      background: var(--pages-neutral-2, #f5f5f5);
      border: 1px solid var(--pages-neutral-5, #d4d4d4);
      cursor: pointer;
      margin-top: var(--pages-space-1, 4px);
      margin-right: var(--pages-space-1, 4px);
    }
    .artefact-chip:hover { background: var(--pages-neutral-3, #e5e5e5); }
    .expand-toggle {
      background: none; border: none; cursor: pointer;
      font-size: var(--pages-font-size-xs, 11px);
      color: var(--pages-neutral-8, #888);
      padding: 2px 4px; border-radius: var(--pages-radius-sm, 4px); line-height: 1;
    }
    .expand-toggle:hover { background: var(--pages-neutral-3, #e5e5e5); color: var(--pages-neutral-11, #333); }
    .expanded-section {
      margin-top: var(--pages-space-2, 8px);
      padding: var(--pages-space-2, 8px);
      border-top: 1px solid var(--pages-neutral-4, #e5e5e5);
      font-size: var(--pages-font-size-xs, 11px);
      color: var(--pages-neutral-9, #737373);
    }
    .correlation-context {
      margin-bottom: var(--pages-space-2, 8px);
      padding: var(--pages-space-1, 4px) var(--pages-space-2, 8px);
      border-left: 2px solid var(--pages-neutral-5, #d4d4d4);
    }
    .correlation-context .parent-sender { font-weight: var(--pages-font-weight-semibold, 600); color: var(--pages-neutral-11, #333); }
    .artefact-detail {
      display: flex; align-items: center; gap: var(--pages-space-2, 8px);
      padding: var(--pages-space-1, 4px) 0;
    }
    .artefact-detail .artefact-uri { color: var(--pages-neutral-8, #888); }
    .artefact-detail .artefact-scope { color: var(--pages-info-9, #2563eb); }
    .commitment-details { margin-bottom: var(--pages-space-2, 8px); }
    .commitment-details .detail-row { display: flex; gap: var(--pages-space-2, 8px); padding: 2px 0; }
    .commitment-details .detail-label { color: var(--pages-neutral-8, #888); }
    .metadata {
      display: flex; gap: var(--pages-space-3, 12px);
      margin-bottom: var(--pages-space-2, 8px);
    }
    .metadata .meta-item { display: flex; gap: var(--pages-space-1, 4px); }
    .metadata .meta-label { color: var(--pages-neutral-8, #888); }
    .action-bar {
      display: flex; gap: var(--pages-space-2, 8px);
      margin-top: var(--pages-space-2, 8px);
      padding-top: var(--pages-space-2, 8px);
      border-top: 1px solid var(--pages-neutral-4, #e5e5e5);
    }
    .reply-btn {
      display: inline-flex; align-items: center; gap: var(--pages-space-1, 4px);
      font-size: var(--pages-font-size-xs, 11px); padding: 4px 10px;
      border-radius: var(--pages-radius-sm, 4px);
      background: var(--pages-neutral-2, #f5f5f5);
      border: 1px solid var(--pages-neutral-5, #d4d4d4);
      cursor: pointer; color: var(--pages-neutral-11, #333);
    }
    .reply-btn:hover { background: var(--pages-neutral-3, #e5e5e5); }
    @media (prefers-reduced-motion: reduce) {
      .expanded-section { transition: none; }
    }
  `;

  private _formatTime(iso: string): string {
    const date = new Date(iso);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 1) return 'now';
    if (diffMin < 60) return `${diffMin}m`;
    const diffHr = Math.floor(diffMin / 60);
    if (diffHr < 24) return `${diffHr}h`;
    return `${Math.floor(diffHr / 24)}d`;
  }

  private _actorIcon(type: string): string {
    switch (type) {
      case 'HUMAN': return '\u{1F464}';
      case 'AGENT': return '\u{1F916}';
      case 'SYSTEM': return '⚙';
      default: return '?';
    }
  }

  override connectedCallback() {
    super.connectedCallback();
    this.addEventListener('keydown', this._onKeydown);
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    this.removeEventListener('keydown', this._onKeydown);
  }

  private _onKeydown = (e: KeyboardEvent) => {
    if (e.key === 'Escape' && this._expanded) {
      this._expanded = false;
    }
  };

  private _toggle() {
    this._expanded = !this._expanded;
  }

  private _onReply() {
    emitChatEvent(this, ChatEventTopics.MESSAGE_SELECTED, { message: this.message });
  }

  private _truncate(text: string, maxLen: number): string {
    if (text.length <= maxLen) return text;
    return text.slice(0, maxLen) + '…';
  }

  private _renderExpanded() {
    const m = this.message;
    return html`
      <div class="expanded-section">
        ${this.parentMessage ? html`
          <div class="correlation-context">
            In reply to <span class="parent-sender">${this.parentMessage.sender}</span>:
            ${this._truncate(this.parentMessage.content, 80)}
          </div>
        ` : nothing}
        ${m.artefactRefs.length > 0 ? html`
          ${m.artefactRefs.map(ref => html`
            <div class="artefact-detail">
              <span data-type=${ref.type}>${ref.label}</span>
              <span class="artefact-uri">${ref.uri}</span>
              ${ref.scope?.startLine != null ? html`
                <span class="artefact-scope">L${ref.scope.startLine}${ref.scope.endLine != null ? `-${ref.scope.endLine}` : ''}</span>
              ` : nothing}
            </div>
          `)}
        ` : nothing}
        ${this.commitmentState && isObligationCreating(m.messageType) ? html`
          <div class="commitment-details">
            <div class="detail-row">
              <span class="detail-label">State:</span>
              <span class="commitment-badge commitment-${commitmentStateCategory(this.commitmentState)}">${this.commitmentState}</span>
            </div>
            ${m.deadline ? html`
              <div class="detail-row">
                <span class="detail-label">Deadline:</span>
                <span>${this._formatTime(m.deadline)}</span>
              </div>
            ` : nothing}
            ${m.acknowledgedAt ? html`
              <div class="detail-row">
                <span class="detail-label">Acknowledged:</span>
                <span>${this._formatTime(m.acknowledgedAt)}</span>
              </div>
            ` : nothing}
          </div>
        ` : nothing}
        <div class="metadata">
          ${m.topic ? html`
            <span class="meta-item"><span class="meta-label">Topic:</span> ${m.topic}</span>
          ` : nothing}
          ${this.channelName ? html`
            <span class="meta-item"><span class="meta-label">Channel:</span> ${this.channelName}</span>
          ` : nothing}
        </div>
        <div class="action-bar">
          <button class="reply-btn" @click=${this._onReply}>↩ Reply</button>
        </div>
      </div>
    `;
  }

  override render() {
    if (!this.message) return nothing;
    const m = this.message;
    const category = messageTypeCategory(m.messageType);

    return html`
      <div class="message-header">
        ${this.showActorBadge ? html`
          <span class="actor-icon" data-actor=${m.actorType}>${this._actorIcon(m.actorType)}</span>
        ` : nothing}
        <span class="sender">${m.sender}</span>
        ${this.showSpeechAct ? html`
          <span class="speech-act-badge badge-${category}">${m.messageType}</span>
        ` : nothing}
        ${this.commitmentState && isObligationCreating(m.messageType) ? html`
          <span class="commitment-badge commitment-${commitmentStateCategory(this.commitmentState)}">${this.commitmentState}</span>
        ` : nothing}
        <time datetime=${m.createdAt}>${this._formatTime(m.createdAt)}</time>
        <button class="expand-toggle" @click=${this._toggle} aria-expanded=${this._expanded}>
          ${this._expanded ? '▼' : '▶'}
        </button>
      </div>
      <div class="content">${unsafeHTML(renderMarkdown(m.content))}</div>
      ${m.messageType === 'HANDOFF' && m.target ? html`
        <div class="delegation-indicator">
          ↳ Delegated to <strong>${m.target}</strong>
        </div>
      ` : nothing}
      ${m.artefactRefs.length > 0 ? html`
        <div class="artefact-chips">
          ${m.artefactRefs.map(ref => html`
            <span class="artefact-chip" data-type=${ref.type}>${ref.label}</span>
          `)}
        </div>
      ` : nothing}
      ${this._expanded ? this._renderExpanded() : nothing}
      <qhorus-reaction-bar .reactions=${this.reactions} .messageId=${m.id}></qhorus-reaction-bar>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'qhorus-message': QhorusMessageElement;
  }
}
