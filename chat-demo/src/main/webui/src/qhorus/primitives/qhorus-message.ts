// chat-demo/src/main/webui/src/qhorus/primitives/qhorus-message.ts
import { LitElement, html, css, nothing } from 'lit';
import { customElement, property } from 'lit/decorators.js';
import { unsafeHTML } from 'lit/directives/unsafe-html.js';
import type { QhorusMessage, Reaction, CommitmentState } from '../types.js';
import { messageTypeCategory, commitmentStateCategory, isObligationCreating } from '../types.js';
import { renderMarkdown } from '../markdown.js';

@customElement('qhorus-message')
export class QhorusMessageElement extends LitElement {
  @property({ type: Object }) message!: QhorusMessage;
  @property({ type: Array }) reactions: Reaction[] = [];
  @property({ type: Boolean }) showSpeechAct = true;
  @property({ type: Boolean }) showActorBadge = true;
  @property({ type: Boolean }) expanded = false;
  @property({ type: String }) commitmentState?: CommitmentState;

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
      ${this.reactions.length > 0 ? html`
        <qhorus-reaction-bar .reactions=${this.reactions}></qhorus-reaction-bar>
      ` : nothing}
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'qhorus-message': QhorusMessageElement;
  }
}
