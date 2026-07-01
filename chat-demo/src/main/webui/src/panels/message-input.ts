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
  private input: HTMLInputElement | null = null;
  private errorTimer: number | null = null;

  constructor() {
    super();
    this.shadow = this.attachShadow({ mode: "open" });
  }

  connectedCallback(): void {
    document.addEventListener("pages-event", this.onEvent);
    this.render();
    this.input = this.shadow.querySelector("input");
    this.input?.addEventListener("keydown", this.onKeydown);
  }

  disconnectedCallback(): void {
    document.removeEventListener("pages-event", this.onEvent);
    this.input?.removeEventListener("keydown", this.onKeydown);
    if (this.errorTimer !== null) {
      clearTimeout(this.errorTimer);
    }
  }

  private onEvent = (e: Event): void => {
    const { topic, payload } = (e as CustomEvent).detail;
    if (topic === "channel-selected") {
      this.currentChannelId = payload.channelId;
      this.updateInputState();
    }
  };

  private onKeydown = async (e: KeyboardEvent): Promise<void> => {
    if (e.key !== "Enter") return;
    e.preventDefault();

    const input = e.target as HTMLInputElement;
    const text = input.value.trim();
    if (!text || !this.currentChannelId) return;

    // Disable during POST
    input.disabled = true;

    try {
      const response = await fetch(`/api/channels/${this.currentChannelId}/messages`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text }),
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      // Clear on success
      input.value = "";
      input.disabled = false;
    } catch (err) {
      // Re-enable and flash red border
      input.disabled = false;
      input.classList.add("error");

      if (this.errorTimer !== null) {
        clearTimeout(this.errorTimer);
      }
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
    this.shadow.innerHTML = `
      <style>${STYLES}</style>
      <div class="input-wrapper">
        <input type="text" placeholder="Select a channel first" disabled />
      </div>
    `;
  }
}

customElements.define("chat-message-input", ChatMessageInput);

export {};
