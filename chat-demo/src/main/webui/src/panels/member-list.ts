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
    gap: 8px;
  }
  .count-badge {
    font-size: 10px;
    font-weight: 500;
    background: var(--pages-border, #3a3a5e);
    color: var(--pages-text-muted, #999);
    padding: 2px 6px;
    border-radius: 10px;
    text-transform: none;
    letter-spacing: 0;
  }
  .member-list {
    flex: 1;
    overflow-y: auto;
    scrollbar-width: thin;
    scrollbar-color: var(--pages-border, #3a3a5e) transparent;
  }
  .member {
    padding: 6px 16px;
    font-size: 13px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    display: flex;
    align-items: center;
  }
  .presence-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    display: inline-block;
    margin-right: 8px;
    flex-shrink: 0;
  }
  .presence-dot.online {
    background: #4caf50;
  }
  .presence-dot.away {
    background: #ffc107;
  }
  .presence-dot.offline {
    background: #757575;
  }
  .member-name {
    overflow: hidden;
    text-overflow: ellipsis;
  }
`;

interface MemberData {
  membershipId: string;
  channelId: string;
  memberId: string;
  displayName: string;
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

class ChatMemberList extends HTMLElement {
  private members: MemberData[] = [];
  private presenceMap: Map<string, string> = new Map();
  private selectedChannelId = "";
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

    if (topic === "channel-selected") {
      this.selectedChannelId = payload.channelId;
      this.render();
      return;
    }

    if (topic !== "ws-data") return;

    const msg = payload as {
      op: string;
      dataset: string;
      rows?: string[][];
      row?: string[];
      key?: string;
    };

    if (msg.dataset === "members") {
      if (msg.op === "snapshot" && msg.rows) {
        this.members = msg.rows.map((r) => ({
          membershipId: r[0],
          channelId: r[1],
          memberId: r[2],
          displayName: r[3],
        }));
        this.render();
      } else if (msg.op === "append" && msg.rows) {
        for (const r of msg.rows) {
          this.members.push({
            membershipId: r[0],
            channelId: r[1],
            memberId: r[2],
            displayName: r[3],
          });
        }
        this.render();
      } else if (msg.op === "remove" && msg.key) {
        this.members = this.members.filter(
          (m) => m.membershipId !== msg.key
        );
        this.render();
      }
    } else if (msg.dataset === "presence") {
      if (msg.op === "snapshot" && msg.rows) {
        this.presenceMap.clear();
        for (const r of msg.rows) {
          this.presenceMap.set(r[0], r[1]);
        }
        this.render();
      } else if (msg.op === "replace" && msg.row && msg.key) {
        this.presenceMap.set(msg.key, msg.row[1]);
        this.render();
      }
    }
  };

  private getFilteredSortedMembers(): MemberData[] {
    const filtered = this.members.filter(
      (m) => m.channelId === this.selectedChannelId
    );

    const statusOrder: Record<string, number> = {
      ONLINE: 0,
      AWAY: 1,
      OFFLINE: 2,
    };

    return filtered.sort((a, b) => {
      const statusA = this.presenceMap.get(a.memberId) || "OFFLINE";
      const statusB = this.presenceMap.get(b.memberId) || "OFFLINE";
      const orderA = statusOrder[statusA] ?? 2;
      const orderB = statusOrder[statusB] ?? 2;

      if (orderA !== orderB) {
        return orderA - orderB;
      }

      return a.displayName.localeCompare(b.displayName);
    });
  }

  private getPresenceClass(memberId: string): string {
    const status = this.presenceMap.get(memberId) || "OFFLINE";
    return status.toLowerCase();
  }

  private render(): void {
    const sortedMembers = this.getFilteredSortedMembers();

    this.shadow.innerHTML = `
      <style>${STYLES}</style>
      <div class="header">
        Members
        <span class="count-badge">${sortedMembers.length}</span>
      </div>
      <div class="member-list">
        ${sortedMembers
          .map(
            (member) => `
          <div class="member">
            <span class="presence-dot ${this.getPresenceClass(member.memberId)}"></span>
            <span class="member-name">${escapeHtml(member.displayName)}</span>
          </div>
        `
          )
          .join("")}
      </div>
    `;
  }
}

customElements.define("chat-member-list", ChatMemberList);

export {};
