const STYLES = `
  :host {
    display: block;
    width: 100%;
    padding: 8px 16px;
    box-sizing: border-box;
    background: var(--pages-bg, #1a1a2e);
    -webkit-font-smoothing: antialiased;
  }
  .input-wrapper {
    position: relative;
    width: 100%;
  }
  .reply-banner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 6px 12px;
    margin-bottom: 4px;
    background: rgba(124, 140, 248, 0.1);
    border-left: 2px solid var(--pages-accent, #7c8cf8);
    border-radius: 0 4px 4px 0;
    font-size: 12px;
    color: var(--pages-text-muted, #999);
  }
  .reply-banner-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .reply-banner-sender {
    font-weight: 600;
    color: var(--pages-accent, #7c8cf8);
  }
  .reply-cancel {
    background: none;
    border: none;
    color: var(--pages-text-muted, #999);
    cursor: pointer;
    font-size: 14px;
    padding: 0 4px;
    line-height: 1;
    flex-shrink: 0;
    margin-left: 8px;
  }
  .reply-cancel:hover {
    color: var(--pages-text, #e0e0e0);
  }
  input {
    width: 100%;
    padding: 10px 16px;
    font-size: 14px;
    font-family: var(--pages-font, system-ui, -apple-system, sans-serif);
    background: var(--pages-bg-alt, #16213e);
    color: var(--pages-text, #e0e0e0);
    border: 1px solid var(--pages-border, #3a3a5e);
    border-radius: 6px;
    outline: none;
    box-sizing: border-box;
    transition: border-color 0.2s, opacity 0.2s;
  }
  input:focus {
    border-color: var(--pages-accent, #7c8cf8);
  }
  input:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
  input.error {
    border-color: #e74c3c;
  }
`;

class ChatMessageInput extends HTMLElement {
  private shadow: ShadowRoot;
  private currentChannelId = "";
  private replyToMessageId: string | null = null;
  private replyToSenderName: string | null = null;
  private input: HTMLInputElement | null = null;
  private errorTimer: number | null = null;

  constructor() {
    super();
    this.shadow = this.attachShadow({ mode: "open" });
  }

  connectedCallback(): void {
    document.addEventListener("pages-event", this.onEvent);
    this.render();
    this.bindInput();
  }

  disconnectedCallback(): void {
    document.removeEventListener("pages-event", this.onEvent);
    if (this.errorTimer !== null) clearTimeout(this.errorTimer);
  }

  private bindInput(): void {
    this.input = this.shadow.querySelector("input");
    this.input?.addEventListener("keydown", this.onKeydown);
  }

  private onEvent = (e: Event): void => {
    const { topic, payload } = (e as CustomEvent).detail;

    if (topic === "channel-selected") {
      this.currentChannelId = payload.channelId;
      this.clearReplyState();
      this.updateInputState();
      return;
    }

    if (topic === "reply-to") {
      const { channelId, messageId, senderName } = payload as { channelId: string; messageId: string; senderName: string };
      if (channelId !== this.currentChannelId) return;
      this.replyToMessageId = messageId;
      this.replyToSenderName = senderName;
      this.render();
      this.bindInput();
      this.input?.focus();
    }
  };

  private clearReplyState(): void {
    this.replyToMessageId = null;
    this.replyToSenderName = null;
    this.render();
    this.bindInput();
  }

  private onKeydown = async (e: KeyboardEvent): Promise<void> => {
    if (e.key !== "Enter") return;
    e.preventDefault();

    const input = e.target as HTMLInputElement;
    const text = input.value.trim();
    if (!text || !this.currentChannelId) return;

    input.disabled = true;

    try {
      let url: string;
      if (this.replyToMessageId) {
        url = `/api/channels/${this.currentChannelId}/messages/${this.replyToMessageId}/replies`;
      } else {
        url = `/api/channels/${this.currentChannelId}/messages`;
      }

      const response = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text }),
      });

      if (!response.ok) throw new Error(`HTTP ${response.status}`);

      input.value = "";
      input.disabled = false;
      if (this.replyToMessageId) this.clearReplyState();
    } catch (err) {
      input.disabled = false;
      input.classList.add("error");

      if (this.errorTimer !== null) clearTimeout(this.errorTimer);
      this.errorTimer = window.setTimeout(() => {
        input.classList.remove("error");
        this.errorTimer = null;
      }, 2000);

      console.error("Failed to send message:", err);
    }
  };

  private updateInputState(): void {
    if (!this.input) return;
    if (this.currentChannelId) {
      this.input.disabled = false;
      this.input.placeholder = "Type a message...";
    } else {
      this.input.disabled = true;
      this.input.placeholder = "Select a channel first";
    }
  }

  private render(): void {
    const replyBanner = this.replyToMessageId && this.replyToSenderName
      ? `<div class="reply-banner">
           <span class="reply-banner-text">Replying to <span class="reply-banner-sender">${this.escapeHtml(this.replyToSenderName)}</span></span>
           <button class="reply-cancel" title="Cancel reply">\u{2715}</button>
         </div>`
      : "";

    this.shadow.innerHTML = `
      <style>${STYLES}</style>
      <div class="input-wrapper">
        ${replyBanner}
        <input type="text" placeholder="${this.currentChannelId ? "Type a message..." : "Select a channel first"}" ${this.currentChannelId ? "" : "disabled"} />
      </div>
    `;

    this.shadow.querySelector(".reply-cancel")?.addEventListener("click", () => this.clearReplyState());
  }

  private escapeHtml(text: string): string {
    return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }
}

customElements.define("chat-message-input", ChatMessageInput);

export {};
