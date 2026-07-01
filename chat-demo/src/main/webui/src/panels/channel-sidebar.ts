const STYLES = `
  :host {
    display: flex;
    flex-direction: column;
    height: 100%;
    overflow: hidden;
    font-family: var(--pages-font, system-ui, -apple-system, sans-serif);
    background: var(--pages-bg, #1a1a2e);
    color: var(--pages-text, #e0e0e0);
    -webkit-font-smoothing: antialiased;
  }
  .header {
    padding: 12px 16px 8px;
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--pages-text-muted, #999);
  }
  .channel-list {
    flex: 1;
    overflow-y: auto;
    scrollbar-width: thin;
    scrollbar-color: var(--pages-border, #3a3a5e) transparent;
  }
  .channel {
    padding: 6px 16px;
    font-size: 14px;
    cursor: pointer;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    border-radius: 4px;
    margin: 1px 8px;
    transition: background 0.15s;
  }
  .channel:hover {
    background: var(--pages-bg-hover, #1e3a5f);
  }
  .channel.selected {
    background: var(--pages-bg-hover, #1e3a5f);
    color: var(--pages-accent, #7c8cf8);
    font-weight: 600;
  }
  .channel-hash {
    color: var(--pages-text-muted, #999);
    margin-right: 4px;
    font-weight: 400;
  }
`;

interface ChannelData {
  id: string;
  name: string;
  topic: string;
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

class ChatChannelSidebar extends HTMLElement {
  private channels: ChannelData[] = [];
  private selectedId = "";
  private shadow: ShadowRoot;

  constructor() {
    super();
    this.shadow = this.attachShadow({ mode: "open" });
  }

  connectedCallback(): void {
    document.addEventListener("pages-event", this.onEvent);
    this.render();
  }

  disconnectedCallback(): void {
    document.removeEventListener("pages-event", this.onEvent);
  }

  private onEvent = (e: Event): void => {
    const { topic, payload } = (e as CustomEvent).detail;
    if (topic !== "ws-data") return;
    const msg = payload as { op: string; dataset: string; rows?: string[][]; columns?: unknown[] };
    if (msg.dataset !== "channels") return;

    if (msg.op === "snapshot" && msg.rows) {
      this.channels = msg.rows.map((r) => ({ id: r[0], name: r[1], topic: r[2] }));
      this.render();
      if (this.channels.length > 0 && !this.selectedId) {
        queueMicrotask(() => this.selectChannel(this.channels[0].id));
      }
    } else if (msg.op === "append" && msg.rows) {
      for (const r of msg.rows) {
        this.channels.push({ id: r[0], name: r[1], topic: r[2] });
      }
      this.render();
    }
  };

  private selectChannel(id: string): void {
    this.selectedId = id;
    this.render();
    this.dispatchEvent(new CustomEvent("pages-event", {
      bubbles: true,
      composed: true,
      detail: { topic: "channel-selected", payload: { channelId: id } },
    }));
  }

  private render(): void {
    this.shadow.innerHTML = `
      <style>${STYLES}</style>
      <div class="header">Channels</div>
      <div class="channel-list">
        ${this.channels.map((ch) => `
          <div class="channel ${ch.id === this.selectedId ? "selected" : ""}"
               data-id="${escapeHtml(ch.id)}"
               title="${escapeHtml(ch.topic || ch.name)}">
            <span class="channel-hash">#</span>${escapeHtml(ch.name)}
          </div>
        `).join("")}
      </div>
    `;
    this.shadow.querySelectorAll(".channel").forEach((el) => {
      el.addEventListener("click", () => {
        this.selectChannel((el as HTMLElement).dataset.id!);
      });
    });
  }
}

customElements.define("chat-channel-sidebar", ChatChannelSidebar);

export {};
