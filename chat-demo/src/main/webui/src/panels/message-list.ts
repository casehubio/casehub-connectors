import { authenticatedFetch } from "../auth.js";

const STYLES = `
  :host {
    display: flex;
    flex-direction: column;
    height: 100%;
    overflow: hidden;
    font-family: var(--pages-font, system-ui, -apple-system, sans-serif);
    background: var(--pages-bg, #1a1a2e);
    color: var(--pages-text, #e0e0e0);
    position: relative;
    -webkit-font-smoothing: antialiased;
  }

  .scroll-container {
    flex: 1;
    overflow-y: auto;
    scrollbar-width: thin;
    scrollbar-color: var(--pages-border, #3a3a5e) transparent;
  }

  .message-list {
    display: flex;
    flex-direction: column;
    min-height: 100%;
    justify-content: flex-end;
  }

  .empty-state {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    color: var(--pages-text-muted, #999);
    font-size: 14px;
    font-style: italic;
    user-select: none;
  }

  .message {
    padding: 8px 16px;
    margin-bottom: 2px;
    transition: background 0.1s;
    position: relative;
  }

  .message:hover {
    background: var(--pages-bg-hover, #1e3a5f);
  }

  .message.grouped {
    padding-top: 2px;
  }

  .message-header {
    display: flex;
    align-items: baseline;
    gap: 8px;
    margin-bottom: 2px;
  }

  .sender-name {
    font-size: 13px;
    font-weight: 700;
    color: var(--pages-accent, #7c8cf8);
    flex-shrink: 0;
  }

  .timestamp {
    font-size: 11px;
    color: var(--pages-text-muted, #999);
    flex-shrink: 0;
    margin-left: auto;
  }

  .message-text {
    font-size: 14px;
    line-height: 1.5;
    word-wrap: break-word;
    overflow-wrap: break-word;
    white-space: pre-wrap;
  }

  /* Reply reference bar */
  .reply-ref {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: var(--pages-text-muted, #999);
    padding: 4px 8px;
    margin-bottom: 4px;
    background: rgba(124, 140, 248, 0.08);
    border-left: 2px solid var(--pages-accent, #7c8cf8);
    border-radius: 0 4px 4px 0;
    cursor: pointer;
    transition: background 0.15s;
  }
  .reply-ref:hover {
    background: rgba(124, 140, 248, 0.15);
  }
  .reply-ref-sender {
    font-weight: 600;
    color: var(--pages-accent, #7c8cf8);
  }
  .reply-ref-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 300px;
  }

  /* Action bar */
  .action-bar {
    display: none;
    position: absolute;
    top: 4px;
    right: 8px;
    background: var(--pages-bg-alt, #16213e);
    border: 1px solid var(--pages-border, #3a3a5e);
    border-radius: 4px;
    padding: 2px;
    gap: 2px;
    z-index: 5;
  }
  .message:hover .action-bar {
    display: flex;
  }
  .action-btn {
    background: none;
    border: none;
    cursor: pointer;
    font-size: 14px;
    padding: 4px 6px;
    border-radius: 3px;
    line-height: 1;
    transition: background 0.15s;
  }
  .action-btn:hover {
    background: var(--pages-bg-hover, #1e3a5f);
  }

  /* Emoji palette */
  .emoji-palette {
    position: absolute;
    top: 100%;
    right: 0;
    background: var(--pages-bg-alt, #16213e);
    border: 1px solid var(--pages-border, #3a3a5e);
    border-radius: 6px;
    padding: 6px;
    display: flex;
    gap: 4px;
    z-index: 20;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  }
  .emoji-btn {
    background: none;
    border: none;
    cursor: pointer;
    font-size: 18px;
    padding: 4px 6px;
    border-radius: 4px;
    line-height: 1;
    transition: background 0.15s;
  }
  .emoji-btn:hover {
    background: var(--pages-bg-hover, #1e3a5f);
  }

  /* Reaction pills */
  .reactions {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    margin-top: 4px;
  }
  .reaction-pill {
    display: inline-flex;
    align-items: center;
    font-size: 13px;
    padding: 2px 8px;
    background: rgba(124, 140, 248, 0.1);
    border: 1px solid rgba(124, 140, 248, 0.2);
    border-radius: 12px;
    cursor: pointer;
    transition: background 0.15s, border-color 0.15s;
    user-select: none;
  }
  .reaction-pill:hover {
    background: rgba(124, 140, 248, 0.2);
    border-color: rgba(124, 140, 248, 0.4);
  }

  .highlight {
    animation: flash 1s ease-out;
  }
  @keyframes flash {
    0% { background: rgba(124, 140, 248, 0.25); }
    100% { background: transparent; }
  }

  .new-messages-pill {
    position: absolute;
    bottom: 12px;
    left: 50%;
    transform: translateX(-50%);
    background: var(--pages-accent, #7c8cf8);
    color: #fff;
    font-size: 12px;
    font-weight: 600;
    padding: 6px 16px;
    border-radius: 16px;
    cursor: pointer;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
    z-index: 10;
    user-select: none;
    transition: opacity 0.2s, transform 0.2s;
    opacity: 0;
    pointer-events: none;
  }

  .new-messages-pill.visible {
    opacity: 1;
    pointer-events: auto;
  }

  .new-messages-pill:hover {
    filter: brightness(1.1);
  }
`;

const MONTH_NAMES = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const GROUP_THRESHOLD_MS = 2 * 60 * 1000;
const SCROLL_BOTTOM_THRESHOLD = 40;
const EMOJIS = ["\u{1F44D}", "\u{2764}\u{FE0F}", "\u{1F602}", "\u{1F389}", "\u{1F440}", "\u{1F525}"];

interface MessageData {
  channelId: string;
  messageId: string;
  parentId: string;
  senderId: string;
  text: string;
  timestamp: string;
}

interface ReactionData {
  messageId: string;
  emoji: string;
}

function parseRow(r: string[]): MessageData {
  return { channelId: r[0], messageId: r[1], parentId: r[2], senderId: r[3], text: r[4], timestamp: r[5] };
}

function formatTimestamp(iso: string): string {
  const date = new Date(iso);
  const now = new Date();
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  const time = `${hh}:${mm}`;
  const isToday = date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth() && date.getDate() === now.getDate();
  if (isToday) return time;
  return `${MONTH_NAMES[date.getMonth()]} ${date.getDate()}, ${time}`;
}

function shouldGroup(prev: MessageData, curr: MessageData): boolean {
  if (prev.senderId !== curr.senderId) return false;
  if (curr.parentId) return false;
  if (prev.parentId) return false;
  return new Date(curr.timestamp).getTime() - new Date(prev.timestamp).getTime() < GROUP_THRESHOLD_MS;
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

class ChatMessageList extends HTMLElement {
  private messages: MessageData[] = [];
  private reactions: ReactionData[] = [];
  private selectedChannelId = "";
  private shadow: ShadowRoot;
  private hasNewMessages = false;
  private emojiPaletteMessageId: string | null = null;

  constructor() {
    super();
    this.shadow = this.attachShadow({ mode: "open" });
  }

  connectedCallback(): void {
    document.addEventListener("pages-event", this.onEvent);
    document.addEventListener("click", this.onDocumentClick);
    this.render();
    this.shadow.addEventListener("scroll", this.onScroll, true);
    this.shadow.addEventListener("click", this.onClickDelegated);
  }

  disconnectedCallback(): void {
    document.removeEventListener("pages-event", this.onEvent);
    document.removeEventListener("click", this.onDocumentClick);
    this.shadow.removeEventListener("scroll", this.onScroll, true);
    this.shadow.removeEventListener("click", this.onClickDelegated);
  }

  private onDocumentClick = (): void => {
    if (this.emojiPaletteMessageId) {
      this.emojiPaletteMessageId = null;
      this.closePalette();
    }
  };

  private closePalette(): void {
    this.shadow.querySelectorAll(".emoji-palette").forEach((el) => el.remove());
  }

  private onEvent = (e: Event): void => {
    const { topic, payload } = (e as CustomEvent).detail;

    if (topic === "channel-selected") {
      this.selectedChannelId = (payload as { channelId: string }).channelId;
      this.hasNewMessages = false;
      this.render();
      this.scrollToBottom();
      return;
    }

    if (topic !== "ws-data") return;
    const msg = payload as { op: string; dataset: string; rows?: string[][]; key?: string };

    if (msg.dataset === "messages") {
      if (msg.op === "snapshot" && msg.rows) {
        this.messages = msg.rows.map(parseRow);
        this.render();
        this.scrollToBottom();
      } else if (msg.op === "append" && msg.rows) {
        const wasAtBottom = this.isAtBottom();
        for (const r of msg.rows) this.messages.push(parseRow(r));
        this.render();
        if (wasAtBottom) this.scrollToBottom();
        else this.showNewMessagesPill();
      }
    } else if (msg.dataset === "reactions") {
      if (msg.op === "snapshot" && msg.rows) {
        this.reactions = msg.rows.map((r) => ({ messageId: r[0], emoji: r[1] }));
        this.render();
      } else if (msg.op === "append" && msg.rows) {
        for (const r of msg.rows) this.reactions.push({ messageId: r[0], emoji: r[1] });
        this.render();
      } else if (msg.op === "remove" && msg.key) {
        const sepIdx = msg.key.indexOf(":");
        const messageId = msg.key.substring(0, sepIdx);
        const emoji = msg.key.substring(sepIdx + 1);
        this.reactions = this.reactions.filter((r) => !(r.messageId === messageId && r.emoji === emoji));
        this.render();
      }
    }
  };

  private isAtBottom(): boolean {
    const scroller = this.shadow.querySelector(".scroll-container");
    if (!scroller) return true;
    return scroller.scrollHeight - scroller.scrollTop - scroller.clientHeight < SCROLL_BOTTOM_THRESHOLD;
  }

  private scrollToBottom(): void {
    requestAnimationFrame(() => {
      const scroller = this.shadow.querySelector(".scroll-container");
      if (scroller) scroller.scrollTop = scroller.scrollHeight;
    });
  }

  private scrollToMessage(messageId: string): void {
    const el = this.shadow.querySelector(`[data-msg-id="${messageId}"]`) as HTMLElement;
    if (el) {
      el.scrollIntoView({ behavior: "smooth", block: "center" });
      el.classList.add("highlight");
      setTimeout(() => el.classList.remove("highlight"), 1000);
    }
  }

  private showNewMessagesPill(): void {
    this.hasNewMessages = true;
    const pill = this.shadow.querySelector(".new-messages-pill");
    if (pill) pill.classList.add("visible");
  }

  private hideNewMessagesPill(): void {
    this.hasNewMessages = false;
    const pill = this.shadow.querySelector(".new-messages-pill");
    if (pill) pill.classList.remove("visible");
  }

  private onClickDelegated = (e: Event): void => {
    const target = e.target as HTMLElement;

    if (target.classList.contains("new-messages-pill")) {
      this.hideNewMessagesPill();
      this.scrollToBottom();
      return;
    }

    if (target.classList.contains("reply-ref") || target.closest(".reply-ref")) {
      const refEl = target.closest(".reply-ref") as HTMLElement;
      const parentId = refEl?.dataset.parentId;
      if (parentId) this.scrollToMessage(parentId);
      return;
    }

    if (target.dataset.actionReply) {
      e.stopPropagation();
      const msgId = target.dataset.actionReply;
      const msg = this.messages.find((m) => m.messageId === msgId);
      if (msg) {
        this.dispatchEvent(new CustomEvent("pages-event", {
          bubbles: true,
          composed: true,
          detail: {
            topic: "reply-to",
            payload: {
              channelId: msg.channelId,
              messageId: msg.messageId,
              senderName: msg.senderId,
            },
          },
        }));
      }
      return;
    }

    if (target.dataset.actionEmoji) {
      e.stopPropagation();
      const msgId = target.dataset.actionEmoji;
      if (this.emojiPaletteMessageId === msgId) {
        this.emojiPaletteMessageId = null;
        this.closePalette();
      } else {
        this.emojiPaletteMessageId = msgId;
        this.showEmojiPalette(target, msgId!);
      }
      return;
    }

    if (target.classList.contains("emoji-btn")) {
      e.stopPropagation();
      const emoji = target.dataset.emoji!;
      const msgId = target.dataset.forMsg!;
      this.emojiPaletteMessageId = null;
      this.closePalette();
      this.toggleReaction(msgId, emoji);
      return;
    }

    if (target.classList.contains("reaction-pill")) {
      const emoji = target.dataset.emoji!;
      const msgId = target.dataset.forMsg!;
      this.toggleReaction(msgId, emoji);
      return;
    }
  };

  private showEmojiPalette(anchor: HTMLElement, messageId: string): void {
    this.closePalette();
    const bar = anchor.closest(".action-bar");
    if (!bar) return;
    const palette = document.createElement("div");
    palette.className = "emoji-palette";
    palette.innerHTML = EMOJIS.map((e) =>
      `<button class="emoji-btn" data-emoji="${e}" data-for-msg="${escapeHtml(messageId)}">${e}</button>`
    ).join("");
    bar.appendChild(palette);
  }

  private async toggleReaction(messageId: string, emoji: string): Promise<void> {
    const existing = this.reactions.find((r) => r.messageId === messageId && r.emoji === emoji);
    const channelId = this.selectedChannelId;
    if (existing) {
      await authenticatedFetch(`/api/channels/${channelId}/messages/${messageId}/reactions/${encodeURIComponent(emoji)}`, { method: "DELETE" });
    } else {
      await authenticatedFetch(`/api/channels/${channelId}/messages/${messageId}/reactions`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ emoji }),
      });
    }
  }

  private onScroll = (): void => {
    if (this.hasNewMessages && this.isAtBottom()) this.hideNewMessagesPill();
  };

  private getFilteredMessages(): MessageData[] {
    if (!this.selectedChannelId) return [];
    return this.messages.filter((m) => m.channelId === this.selectedChannelId);
  }

  private getReactionsForMessage(messageId: string): string[] {
    return this.reactions.filter((r) => r.messageId === messageId).map((r) => r.emoji);
  }

  private renderReplyRef(msg: MessageData): string {
    if (!msg.parentId) return "";
    const parent = this.messages.find((m) => m.messageId === msg.parentId);
    if (!parent) return "";
    const truncated = parent.text.length > 60 ? parent.text.substring(0, 60) + "..." : parent.text;
    return `
      <div class="reply-ref" data-parent-id="${escapeHtml(msg.parentId)}">
        ↩ <span class="reply-ref-sender">${escapeHtml(parent.senderId)}</span>
        <span class="reply-ref-text">${escapeHtml(truncated)}</span>
      </div>
    `;
  }

  private renderReactions(messageId: string): string {
    const emojis = this.getReactionsForMessage(messageId);
    if (emojis.length === 0) return "";
    return `<div class="reactions">${emojis.map((e) =>
      `<span class="reaction-pill" data-emoji="${e}" data-for-msg="${messageId}">${e}</span>`
    ).join("")}</div>`;
  }

  private renderActionBar(messageId: string): string {
    return `
      <div class="action-bar">
        <button class="action-btn" data-action-reply="${messageId}" title="Reply">↩</button>
        <button class="action-btn" data-action-emoji="${messageId}" title="React">\u{1F60A}</button>
      </div>
    `;
  }

  private renderMessages(filtered: MessageData[]): string {
    if (filtered.length === 0) return `<div class="empty-state">No messages yet</div>`;

    const parts: string[] = [];
    for (let i = 0; i < filtered.length; i++) {
      const msg = filtered[i];
      const grouped = i > 0 && shouldGroup(filtered[i - 1], msg);
      const replyRef = this.renderReplyRef(msg);
      const reactions = this.renderReactions(msg.messageId);
      const actionBar = this.renderActionBar(msg.messageId);

      if (grouped) {
        parts.push(`
          <div class="message grouped" data-msg-id="${msg.messageId}">
            ${actionBar}
            <div class="message-text">${escapeHtml(msg.text)}</div>
            ${reactions}
          </div>
        `);
      } else {
        parts.push(`
          <div class="message" data-msg-id="${msg.messageId}">
            ${actionBar}
            ${replyRef}
            <div class="message-header">
              <span class="sender-name">${escapeHtml(msg.senderId)}</span>
              <span class="timestamp">${formatTimestamp(msg.timestamp)}</span>
            </div>
            <div class="message-text">${escapeHtml(msg.text)}</div>
            ${reactions}
          </div>
        `);
      }
    }
    return parts.join("");
  }

  private render(): void {
    const filtered = this.getFilteredMessages();
    const hasMessages = filtered.length > 0;

    this.shadow.innerHTML = `
      <style>${STYLES}</style>
      <div class="scroll-container">
        ${hasMessages
          ? `<div class="message-list">${this.renderMessages(filtered)}</div>`
          : this.renderMessages(filtered)
        }
      </div>
      <div class="new-messages-pill ${this.hasNewMessages ? "visible" : ""}">New messages ↓</div>
    `;
  }
}

customElements.define("chat-message-list", ChatMessageList);

export {};
