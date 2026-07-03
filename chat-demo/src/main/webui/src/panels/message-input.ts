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
  textarea {
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
    resize: none;
    overflow-y: hidden;
    min-height: 42px;
    max-height: 200px;
    line-height: 1.4;
    rows: 1;
  }
  textarea:focus {
    border-color: var(--pages-accent, #7c8cf8);
  }
  textarea:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
  textarea.error {
    border-color: #e74c3c;
  }
`;

class ChatMessageInput extends HTMLElement {
  private shadow: ShadowRoot;
  private currentChannelId = "";
  private replyToMessageId: string | null = null;
  private replyToSenderName: string | null = null;
  private textarea: HTMLTextAreaElement | null = null;
  private errorTimer: number | null = null;

  constructor() {
    super();
    this.shadow = this.attachShadow({ mode: "open" });
  }

  connectedCallback(): void {
    document.addEventListener("pages-event", this.onEvent);
    this.render();
    this.bindTextarea();
  }

  disconnectedCallback(): void {
    document.removeEventListener("pages-event", this.onEvent);
    if (this.errorTimer !== null) clearTimeout(this.errorTimer);
  }

  private bindTextarea(): void {
    this.textarea = this.shadow.querySelector("textarea");
    this.textarea?.addEventListener("keydown", this.onKeydown);
    this.textarea?.addEventListener("input", this.onAutoResize);
  }

  private onAutoResize = (): void => {
    const ta = this.textarea;
    if (!ta) return;
    ta.style.height = "auto";
    ta.style.height = `${Math.min(ta.scrollHeight, 200)}px`;
    ta.style.overflowY = ta.scrollHeight > 200 ? "auto" : "hidden";
  };

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
      this.bindTextarea();
      this.textarea?.focus();
    }
  };

  private clearReplyState(): void {
    this.replyToMessageId = null;
    this.replyToSenderName = null;
    this.render();
    this.bindTextarea();
  }

  private onKeydown = async (e: KeyboardEvent): Promise<void> => {
    if (e.key !== "Enter") return;
    if (e.shiftKey) return;
    e.preventDefault();

    const ta = e.target as HTMLTextAreaElement;
    const text = ta.value.trim();
    if (!text || !this.currentChannelId) return;

    ta.disabled = true;

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

      ta.value = "";
      ta.disabled = false;
      ta.style.height = "auto";
      ta.style.overflowY = "hidden";
      if (this.replyToMessageId) this.clearReplyState();
    } catch (err) {
      ta.disabled = false;
      ta.classList.add("error");

      if (this.errorTimer !== null) clearTimeout(this.errorTimer);
      this.errorTimer = window.setTimeout(() => {
        ta.classList.remove("error");
        this.errorTimer = null;
      }, 2000);

      console.error("Failed to send message:", err);
    }
  };

  private updateInputState(): void {
    if (!this.textarea) return;
    if (this.currentChannelId) {
      this.textarea.disabled = false;
      this.textarea.placeholder = "Type a message...";
    } else {
      this.textarea.disabled = true;
      this.textarea.placeholder = "Select a channel first";
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
        <textarea rows="1" placeholder="${this.currentChannelId ? "Type a message..." : "Select a channel first"}" ${this.currentChannelId ? "" : "disabled"}></textarea>
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
