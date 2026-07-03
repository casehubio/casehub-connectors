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
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .add-btn {
    font-size: 16px;
    cursor: pointer;
    color: var(--pages-text-muted, #999);
    background: none;
    border: none;
    padding: 0 4px;
    line-height: 1;
    transition: color 0.15s;
  }
  .add-btn:hover {
    color: var(--pages-accent, #7c8cf8);
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
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .channel:hover {
    background: var(--pages-bg-hover, #1e3a5f);
  }
  .channel.selected {
    background: var(--pages-bg-hover, #1e3a5f);
    color: var(--pages-accent, #7c8cf8);
    font-weight: 600;
  }
  .channel-name {
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .channel-hash {
    color: var(--pages-text-muted, #999);
    margin-right: 4px;
    font-weight: 400;
  }
  .delete-btn {
    display: none;
    font-size: 13px;
    cursor: pointer;
    color: var(--pages-text-muted, #999);
    background: none;
    border: none;
    padding: 2px 4px;
    line-height: 1;
    flex-shrink: 0;
    transition: color 0.15s;
  }
  .delete-btn:hover {
    color: #e74c3c;
  }
  .channel:hover .delete-btn {
    display: block;
  }

  /* Create modal */
  .modal-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 100;
  }
  .modal {
    background: var(--pages-bg-alt, #16213e);
    border: 1px solid var(--pages-border, #3a3a5e);
    border-radius: 8px;
    padding: 24px;
    min-width: 320px;
    max-width: 400px;
  }
  .modal h3 {
    margin: 0 0 16px;
    font-size: 16px;
    font-weight: 600;
    color: var(--pages-text, #e0e0e0);
  }
  .modal label {
    display: block;
    font-size: 12px;
    font-weight: 600;
    color: var(--pages-text-muted, #999);
    margin-bottom: 4px;
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }
  .modal input[type="text"] {
    width: 100%;
    padding: 8px 12px;
    font-size: 14px;
    font-family: var(--pages-font, system-ui, -apple-system, sans-serif);
    background: var(--pages-bg, #1a1a2e);
    color: var(--pages-text, #e0e0e0);
    border: 1px solid var(--pages-border, #3a3a5e);
    border-radius: 4px;
    outline: none;
    box-sizing: border-box;
    margin-bottom: 12px;
  }
  .modal input[type="text"]:focus {
    border-color: var(--pages-accent, #7c8cf8);
  }
  .modal .checkbox-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 16px;
    font-size: 14px;
  }
  .modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
  .modal-actions button {
    padding: 8px 16px;
    font-size: 13px;
    font-family: var(--pages-font, system-ui, -apple-system, sans-serif);
    border-radius: 4px;
    cursor: pointer;
    border: none;
  }
  .btn-cancel {
    background: var(--pages-bg, #1a1a2e);
    color: var(--pages-text, #e0e0e0);
    border: 1px solid var(--pages-border, #3a3a5e) !important;
  }
  .btn-create {
    background: var(--pages-accent, #7c8cf8);
    color: #fff;
    font-weight: 600;
  }
  .btn-create:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  /* Delete confirmation */
  .confirm-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 100;
  }
  .confirm-dialog {
    background: var(--pages-bg-alt, #16213e);
    border: 1px solid var(--pages-border, #3a3a5e);
    border-radius: 8px;
    padding: 24px;
    min-width: 300px;
  }
  .confirm-dialog p {
    margin: 0 0 16px;
    font-size: 14px;
    color: var(--pages-text, #e0e0e0);
  }
  .btn-delete {
    background: #e74c3c;
    color: #fff;
    font-weight: 600;
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
  private showCreateModal = false;
  private deleteTarget: ChannelData | null = null;

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
    const msg = payload as { op: string; dataset: string; rows?: string[][]; key?: string };
    if (msg.dataset !== "channels") return;

    if (msg.op === "snapshot" && msg.rows) {
      this.channels = msg.rows.map((r) => ({ id: r[0], name: r[1], topic: r[2] }));
      this.render();
      if (this.channels.length > 0 && !this.selectedId) {
        queueMicrotask(() => this.selectChannel(this.channels[0].id));
      }
    } else if (msg.op === "append" && msg.rows) {
      for (const r of msg.rows) {
        if (!this.channels.some((ch) => ch.id === r[0])) {
          this.channels.push({ id: r[0], name: r[1], topic: r[2] });
        }
      }
      this.render();
    } else if (msg.op === "remove" && msg.key) {
      const removedId = msg.key;
      this.channels = this.channels.filter((ch) => ch.id !== removedId);
      if (this.selectedId === removedId) {
        this.selectedId = this.channels.length > 0 ? this.channels[0].id : "";
        this.render();
        if (this.selectedId) {
          queueMicrotask(() => this.selectChannel(this.selectedId));
        }
      } else {
        this.render();
      }
    }
  };

  private selectChannel(id: string): void {
    this.selectedId = id;
    this.render();
    this.dispatchEvent(new CustomEvent("pages-event", {
      bubbles: true,
      composed: true,
      detail: { topic: "channel-selected", payload: { channelId: id, channelName: this.channels.find((ch) => ch.id === id)?.name ?? "" } },
    }));
  }

  private openCreateModal(): void {
    this.showCreateModal = true;
    this.render();
    queueMicrotask(() => {
      const nameInput = this.shadow.querySelector(".modal input[type=\"text\"]") as HTMLInputElement;
      nameInput?.focus();
    });
  }

  private closeCreateModal(): void {
    this.showCreateModal = false;
    this.render();
  }

  private async submitCreate(): Promise<void> {
    const nameInput = this.shadow.querySelector("#create-name") as HTMLInputElement;
    const topicInput = this.shadow.querySelector("#create-topic") as HTMLInputElement;
    const descInput = this.shadow.querySelector("#create-desc") as HTMLInputElement;
    const privateCheck = this.shadow.querySelector("#create-private") as HTMLInputElement;

    const name = nameInput?.value.trim();
    if (!name) return;

    try {
      const resp = await fetch("/api/channels", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name,
          topic: topicInput?.value.trim() || "",
          description: descInput?.value.trim() || "",
          isPrivate: privateCheck?.checked || false,
        }),
      });
      if (resp.ok) {
        const channel = await resp.json();
        const ref = channel.ref;
        if (!this.channels.some((ch) => ch.id === ref.id)) {
          this.channels.push({ id: ref.id, name: ref.name ?? name, topic: ref.topic ?? "" });
        }
        this.closeCreateModal();
        queueMicrotask(() => this.selectChannel(ref.id));
      }
    } catch (err) {
      console.error("Failed to create channel:", err);
    }
  }

  private confirmDelete(ch: ChannelData): void {
    this.deleteTarget = ch;
    this.render();
  }

  private cancelDelete(): void {
    this.deleteTarget = null;
    this.render();
  }

  private async executeDelete(): Promise<void> {
    if (!this.deleteTarget) return;
    const id = this.deleteTarget.id;
    this.deleteTarget = null;

    try {
      await fetch(`/api/channels/${id}`, { method: "DELETE" });
    } catch (err) {
      console.error("Failed to delete channel:", err);
    }
  }

  private render(): void {
    this.shadow.innerHTML = `
      <style>${STYLES}</style>
      <div class="header">
        <span>Channels</span>
        <button class="add-btn" title="Create channel">+</button>
      </div>
      <div class="channel-list">
        ${this.channels.map((ch) => `
          <div class="channel ${ch.id === this.selectedId ? "selected" : ""}"
               data-id="${escapeHtml(ch.id)}"
               title="${escapeHtml(ch.topic || ch.name)}">
            <span class="channel-name"><span class="channel-hash">#</span>${escapeHtml(ch.name)}</span>
            <button class="delete-btn" data-delete-id="${escapeHtml(ch.id)}" title="Delete channel">\u{1F5D1}</button>
          </div>
        `).join("")}
      </div>
      ${this.showCreateModal ? this.renderCreateModal() : ""}
      ${this.deleteTarget ? this.renderDeleteConfirm() : ""}
    `;

    this.shadow.querySelector(".add-btn")?.addEventListener("click", (e) => {
      e.stopPropagation();
      this.openCreateModal();
    });

    this.shadow.querySelectorAll(".channel").forEach((el) => {
      el.addEventListener("click", (e) => {
        if ((e.target as HTMLElement).classList.contains("delete-btn")) return;
        this.selectChannel((el as HTMLElement).dataset.id!);
      });
    });

    this.shadow.querySelectorAll(".delete-btn").forEach((el) => {
      el.addEventListener("click", (e) => {
        e.stopPropagation();
        const id = (el as HTMLElement).dataset.deleteId!;
        const ch = this.channels.find((c) => c.id === id);
        if (ch) this.confirmDelete(ch);
      });
    });

    // Modal event handlers
    this.shadow.querySelector(".modal-overlay")?.addEventListener("click", (e) => {
      if (e.target === e.currentTarget) this.closeCreateModal();
    });
    this.shadow.querySelector(".modal .btn-cancel")?.addEventListener("click", () => this.closeCreateModal());
    this.shadow.querySelector(".btn-create")?.addEventListener("click", () => this.submitCreate());
    this.shadow.querySelector("#create-name")?.addEventListener("keydown", (e) => {
      if ((e as KeyboardEvent).key === "Enter") this.submitCreate();
    });

    // Delete confirm handlers
    this.shadow.querySelector(".confirm-overlay")?.addEventListener("click", (e) => {
      if (e.target === e.currentTarget) this.cancelDelete();
    });
    this.shadow.querySelector(".btn-confirm-cancel")?.addEventListener("click", () => this.cancelDelete());
    this.shadow.querySelector(".btn-delete")?.addEventListener("click", () => this.executeDelete());
  }

  private renderCreateModal(): string {
    return `
      <div class="modal-overlay">
        <div class="modal">
          <h3>Create Channel</h3>
          <label for="create-name">Name</label>
          <input type="text" id="create-name" placeholder="new-channel" />
          <label for="create-topic">Topic</label>
          <input type="text" id="create-topic" placeholder="What's this channel about?" />
          <label for="create-desc">Description</label>
          <input type="text" id="create-desc" placeholder="Optional description" />
          <div class="checkbox-row">
            <input type="checkbox" id="create-private" />
            <label for="create-private" style="margin:0;text-transform:none;letter-spacing:normal;font-size:14px;">Private channel</label>
          </div>
          <div class="modal-actions">
            <button class="btn-cancel">Cancel</button>
            <button class="btn-create">Create</button>
          </div>
        </div>
      </div>
    `;
  }

  private renderDeleteConfirm(): string {
    return `
      <div class="confirm-overlay">
        <div class="confirm-dialog">
          <p>Delete <strong>#${escapeHtml(this.deleteTarget!.name)}</strong>? This removes all messages and cannot be undone.</p>
          <div class="modal-actions">
            <button class="btn-cancel btn-confirm-cancel">Cancel</button>
            <button class="btn-delete">Delete</button>
          </div>
        </div>
      </div>
    `;
  }
}

customElements.define("chat-channel-sidebar", ChatChannelSidebar);

export {};
