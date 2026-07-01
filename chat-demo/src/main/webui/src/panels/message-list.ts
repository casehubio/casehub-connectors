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

/** Two-minute threshold for message grouping (ms). */
const GROUP_THRESHOLD_MS = 2 * 60 * 1000;

/** Distance from bottom (px) within which we consider the user "at the bottom". */
const SCROLL_BOTTOM_THRESHOLD = 40;

interface MessageData {
  channelId: string;
  messageId: string;
  parentId: string;
  senderId: string;
  text: string;
  timestamp: string;
}

function parseRow(r: string[]): MessageData {
  return {
    channelId: r[0],
    messageId: r[1],
    parentId: r[2],
    senderId: r[3],
    text: r[4],
    timestamp: r[5],
  };
}

function formatTimestamp(iso: string): string {
  const date = new Date(iso);
  const now = new Date();
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  const time = `${hh}:${mm}`;

  const isToday =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate();

  if (isToday) return time;
  return `${MONTH_NAMES[date.getMonth()]} ${date.getDate()}, ${time}`;
}

function shouldGroup(prev: MessageData, curr: MessageData): boolean {
  if (prev.senderId !== curr.senderId) return false;
  const prevTime = new Date(prev.timestamp).getTime();
  const currTime = new Date(curr.timestamp).getTime();
  return currTime - prevTime < GROUP_THRESHOLD_MS;
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

class ChatMessageList extends HTMLElement {
  private messages: MessageData[] = [];
  private selectedChannelId = "";
  private shadow: ShadowRoot;
  private hasNewMessages = false;

  constructor() {
    super();
    this.shadow = this.attachShadow({ mode: "open" });
  }

  connectedCallback(): void {
    document.addEventListener("pages-event", this.onEvent);
    this.render();
    // Add scroll listener once via delegation
    this.shadow.addEventListener("scroll", this.onScroll, true);
    // Add pill click listener once via delegation
    this.shadow.addEventListener("click", this.onPillClickDelegated);
  }

  disconnectedCallback(): void {
    document.removeEventListener("pages-event", this.onEvent);
    this.shadow.removeEventListener("scroll", this.onScroll, true);
    this.shadow.removeEventListener("click", this.onPillClickDelegated);
  }

  private onEvent = (e: Event): void => {
    const { topic, payload } = (e as CustomEvent).detail;

    if (topic === "channel-selected") {
      const { channelId } = payload as { channelId: string };
      this.selectedChannelId = channelId;
      this.hasNewMessages = false;
      this.render();
      this.scrollToBottom();
      return;
    }

    if (topic !== "ws-data") return;
    const msg = payload as { op: string; dataset: string; rows?: string[][] };
    if (msg.dataset !== "messages") return;

    if (msg.op === "snapshot" && msg.rows) {
      this.messages = msg.rows.map(parseRow);
      this.render();
      this.scrollToBottom();
    } else if (msg.op === "append" && msg.rows) {
      const wasAtBottom = this.isAtBottom();
      for (const r of msg.rows) {
        this.messages.push(parseRow(r));
      }
      this.renderAppend(msg.rows.map(parseRow));
      if (wasAtBottom) {
        this.scrollToBottom();
      } else {
        this.showNewMessagesPill();
      }
    }
  };

  private isAtBottom(): boolean {
    const scroller = this.shadow.querySelector(".scroll-container");
    if (!scroller) return true;
    const { scrollTop, scrollHeight, clientHeight } = scroller;
    return scrollHeight - scrollTop - clientHeight < SCROLL_BOTTOM_THRESHOLD;
  }

  private scrollToBottom(): void {
    requestAnimationFrame(() => {
      const scroller = this.shadow.querySelector(".scroll-container");
      if (scroller) {
        scroller.scrollTop = scroller.scrollHeight;
      }
    });
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

  private onPillClickDelegated = (e: Event): void => {
    const target = e.target as HTMLElement;
    if (target.classList.contains("new-messages-pill")) {
      this.hideNewMessagesPill();
      this.scrollToBottom();
    }
  };

  private onScroll = (): void => {
    if (this.hasNewMessages && this.isAtBottom()) {
      this.hideNewMessagesPill();
    }
  };

  private getFilteredMessages(): MessageData[] {
    if (!this.selectedChannelId) return [];
    return this.messages.filter((m) => m.channelId === this.selectedChannelId);
  }

  private renderMessages(filtered: MessageData[]): string {
    if (filtered.length === 0) {
      return `<div class="empty-state">No messages yet</div>`;
    }

    const parts: string[] = [];
    for (let i = 0; i < filtered.length; i++) {
      const msg = filtered[i];
      const grouped = i > 0 && shouldGroup(filtered[i - 1], msg);

      if (grouped) {
        parts.push(`
          <div class="message grouped">
            <div class="message-text">${escapeHtml(msg.text)}</div>
          </div>
        `);
      } else {
        parts.push(`
          <div class="message">
            <div class="message-header">
              <span class="sender-name">${escapeHtml(msg.senderId)}</span>
              <span class="timestamp">${formatTimestamp(msg.timestamp)}</span>
            </div>
            <div class="message-text">${escapeHtml(msg.text)}</div>
          </div>
        `);
      }
    }
    return parts.join("");
  }

  private renderAppend(newMessages: MessageData[]): void {
    const messageList = this.shadow.querySelector(".message-list");
    if (!messageList) {
      // Empty state, do full render
      this.render();
      return;
    }

    const filtered = this.getFilteredMessages();
    const existingCount = filtered.length - newMessages.length;

    for (let i = 0; i < newMessages.length; i++) {
      const msg = newMessages[i];
      if (msg.channelId !== this.selectedChannelId) continue;

      const index = existingCount + i;
      const grouped = index > 0 && shouldGroup(filtered[index - 1], msg);

      const div = document.createElement("div");
      div.className = grouped ? "message grouped" : "message";

      if (grouped) {
        div.innerHTML = `<div class="message-text">${escapeHtml(msg.text)}</div>`;
      } else {
        div.innerHTML = `
          <div class="message-header">
            <span class="sender-name">${escapeHtml(msg.senderId)}</span>
            <span class="timestamp">${formatTimestamp(msg.timestamp)}</span>
          </div>
          <div class="message-text">${escapeHtml(msg.text)}</div>
        `;
      }

      messageList.appendChild(div);
    }
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
