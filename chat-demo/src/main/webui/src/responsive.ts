type Mode = "phone" | "tablet" | "desktop";

const MQ_TABLET = "(min-width: 768px) and (max-width: 1279px)";
const MQ_DESKTOP = "(min-width: 1280px)";

const RESPONSIVE_CSS = `
#app > [data-component-type="columns"] {
  height: 100%;
}
#app [data-slot] > [data-component-type] {
  height: 100%;
}
[data-component-id="chat-area"] > [data-slot="0"] {
  flex: 1 1 0% !important;
}
[data-component-id="chat-area"] > [data-split-handle] {
  display: none !important;
}
[data-component-id="chat-area"] > [data-slot="1"] {
  flex: 0 0 auto !important;
  overflow: visible !important;
}
#app.phone [data-component-type="columns"] {
  grid-template-columns: 1fr !important;
}
#app.phone [data-component-type="columns"] > [data-slot="col-0"] {
  display: none !important;
}
#app.phone [data-split-handle] {
  display: none !important;
}
#app.phone [data-component-id="main-split"] {
  position: relative !important;
}
#app.phone .chat-area-slot {
  position: absolute !important;
  inset: 0 !important;
  display: flex !important;
  flex-direction: column !important;
  overflow: hidden !important;
}
#app.phone [data-component-id="chat-area"] {
  height: auto !important;
  flex: 1 !important;
  min-height: 0 !important;
}
#app.phone .channel-drawer-slot {
  position: fixed !important;
  left: -280px;
  top: 0;
  bottom: 0;
  width: 280px !important;
  flex: none !important;
  overflow: visible !important;
  z-index: 50;
  transition: left 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  will-change: left;
  background: var(--pages-bg, #1a1a2e);
}
#app.phone .channel-drawer-slot.open {
  left: 0;
}
#app.phone .member-drawer-slot {
  position: fixed !important;
  right: -280px;
  top: 0;
  bottom: 0;
  width: 280px !important;
  flex: none !important;
  overflow: visible !important;
  z-index: 50;
  transition: right 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  will-change: right;
  background: var(--pages-bg, #1a1a2e);
}
#app.phone .member-drawer-slot.open {
  right: 0;
}
.responsive-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 40;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.responsive-backdrop.visible {
  opacity: 1;
  pointer-events: auto;
}
.responsive-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 4px;
  background: var(--pages-bg-alt, #16213e);
  border-bottom: 1px solid var(--pages-border, #3a3a5e);
  flex-shrink: 0;
}
.responsive-header button {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  color: var(--pages-text, #e0e0e0);
  font-size: 20px;
  cursor: pointer;
  border-radius: var(--pages-radius, 4px);
  transition: background 0.15s;
  -webkit-font-smoothing: antialiased;
}
.responsive-header button:hover {
  background: var(--pages-bg-hover, #1e3a5f);
}
.responsive-header .channel-name {
  font-family: var(--pages-font, system-ui, -apple-system, sans-serif);
  font-size: 15px;
  font-weight: 600;
  color: var(--pages-text, #e0e0e0);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
#app.tablet [data-component-type="columns"] {
  grid-template-columns: 1fr !important;
}
#app.tablet [data-component-type="columns"] > [data-slot="col-0"] {
  display: none !important;
}
#app.tablet [data-split-handle] {
  display: none !important;
}
#app.tablet .sidebar-with-tabs {
  display: flex !important;
  flex-direction: column !important;
}
#app.tablet .sidebar-with-tabs > [data-component-type] {
  height: auto !important;
  flex: 1 !important;
  min-height: 0 !important;
}
.responsive-tabs {
  display: flex;
  gap: 4px;
  padding: 8px 8px;
  flex-shrink: 0;
}
.responsive-tabs button {
  flex: 1;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  font-family: var(--pages-font, system-ui, -apple-system, sans-serif);
  background: var(--pages-bg, #1a1a2e);
  color: var(--pages-text-muted, #999);
  border: 1px solid var(--pages-border, #3a3a5e);
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.15s;
  -webkit-font-smoothing: antialiased;
}
.responsive-tabs button:hover {
  background: var(--pages-bg-hover, #1e3a5f);
}
.responsive-tabs button.active {
  background: var(--pages-accent, #7c8cf8);
  color: #fff;
  border-color: var(--pages-accent, #7c8cf8);
}
@media (prefers-reduced-motion: reduce) {
  #app.phone .channel-drawer-slot,
  #app.phone .member-drawer-slot,
  .responsive-backdrop,
  .responsive-header button,
  .responsive-tabs button {
    transition-duration: 0ms !important;
  }
}
`;

export class ResponsiveController {
  private container: HTMLElement;
  private styleEl: HTMLStyleElement;
  private globalAbort = new AbortController();
  private modeAbort: AbortController | null = null;
  private currentMode: Mode;

  private currentChannelName = "Chat Demo";
  private tabletActiveTab: "channels" | "members" = "channels";

  private channelSlot: HTMLElement | null = null;
  private memberSlot: HTMLElement | null = null;
  private chatAreaSlot: HTMLElement | null = null;
  private chatAreaEl: HTMLElement | null = null;
  private headerBar: HTMLElement | null = null;
  private backdrop: HTMLElement | null = null;
  private tabSwitcher: HTMLElement | null = null;
  private activeDrawer: "channel" | "member" | null = null;

  constructor(container: HTMLElement) {
    this.container = container;

    this.styleEl = document.createElement("style");
    this.styleEl.dataset.responsive = "";
    this.styleEl.textContent = RESPONSIVE_CSS;
    document.head.appendChild(this.styleEl);

    document.addEventListener("pages-event", this.onChannelSelected, { signal: this.globalAbort.signal });

    const tabletMq = window.matchMedia(MQ_TABLET);
    const desktopMq = window.matchMedia(MQ_DESKTOP);

    this.currentMode = this.detectMode(desktopMq.matches, tabletMq.matches);
    this.applyMode(this.currentMode);

    const onMediaChange = () => {
      const newMode = this.detectMode(desktopMq.matches, tabletMq.matches);
      if (newMode !== this.currentMode) {
        this.teardownMode();
        this.currentMode = newMode;
        this.applyMode(newMode);
      }
    };

    tabletMq.addEventListener("change", onMediaChange, { signal: this.globalAbort.signal });
    desktopMq.addEventListener("change", onMediaChange, { signal: this.globalAbort.signal });
  }

  dispose(): void {
    this.teardownMode();
    this.globalAbort.abort();
    this.styleEl.remove();
    this.container.classList.remove("phone", "tablet", "desktop");
  }

  private onChannelSelected = (e: Event): void => {
    const { topic, payload } = (e as CustomEvent).detail;
    if (topic !== "channel-selected") return;
    const { channelName } = payload as { channelId: string; channelName: string };
    if (channelName) this.currentChannelName = channelName;

    const nameEl = this.headerBar?.querySelector(".channel-name");
    if (nameEl) nameEl.textContent = `#${channelName}`;

    if (this.currentMode === "phone" && this.activeDrawer === "channel") {
      this.closeDrawer();
    }
  };

  private detectMode(desktop: boolean, tablet: boolean): Mode {
    if (desktop) return "desktop";
    if (tablet) return "tablet";
    return "phone";
  }

  private resolveSlots(): void {
    const channelPanelEl = this.container.querySelector<HTMLElement>('[data-component-id="channel-panel"]');
    const memberPanelEl = this.container.querySelector<HTMLElement>('[data-component-id="member-panel"]');
    this.chatAreaEl = this.container.querySelector<HTMLElement>('[data-component-id="chat-area"]');
    this.channelSlot = channelPanelEl?.closest<HTMLElement>("[data-slot]") ?? null;
    this.memberSlot = memberPanelEl?.closest<HTMLElement>("[data-slot]") ?? null;
    this.chatAreaSlot = this.chatAreaEl?.closest<HTMLElement>("[data-slot]") ?? null;
  }

  private applyMode(mode: Mode): void {
    this.container.classList.remove("phone", "tablet", "desktop");
    this.container.classList.add(mode);
    this.resolveSlots();
    this.modeAbort = new AbortController();

    switch (mode) {
      case "phone":
        this.setupPhone();
        break;
      case "tablet":
        this.setupTablet();
        break;
      case "desktop":
        this.setupDesktop();
        break;
    }
  }

  private teardownMode(): void {
    this.modeAbort?.abort();
    this.modeAbort = null;

    this.headerBar?.remove();
    this.headerBar = null;
    this.backdrop?.remove();
    this.backdrop = null;
    this.tabSwitcher?.remove();
    this.tabSwitcher = null;
    this.activeDrawer = null;

    this.channelSlot?.classList.remove("channel-drawer-slot", "open", "sidebar-with-tabs");
    this.memberSlot?.classList.remove("member-drawer-slot", "open", "sidebar-with-tabs");
    this.chatAreaSlot?.classList.remove("chat-area-slot");

    this.channelSlot?.removeAttribute("aria-hidden");
    this.memberSlot?.removeAttribute("aria-hidden");
    this.chatAreaEl?.removeAttribute("inert");
    this.channelSlot?.removeAttribute("inert");
    this.memberSlot?.removeAttribute("inert");

    if (this.channelSlot) this.channelSlot.style.display = "";
    if (this.memberSlot) {
      this.memberSlot.style.display = "";
      this.memberSlot.style.order = "";
    }
    if (this.chatAreaSlot) this.chatAreaSlot.style.flex = "";
  }

  private setupPhone(): void {
    if (!this.channelSlot || !this.memberSlot || !this.chatAreaSlot || !this.chatAreaEl) return;

    this.channelSlot.classList.add("channel-drawer-slot");
    this.memberSlot.classList.add("member-drawer-slot");
    this.chatAreaSlot.classList.add("chat-area-slot");

    this.channelSlot.setAttribute("aria-hidden", "true");
    this.memberSlot.setAttribute("aria-hidden", "true");

    this.headerBar = document.createElement("div");
    this.headerBar.className = "responsive-header";
    const menuBtn = document.createElement("button");
    menuBtn.textContent = "☰";
    menuBtn.title = "Channels";
    menuBtn.setAttribute("aria-expanded", "false");
    const nameSpan = document.createElement("span");
    nameSpan.className = "channel-name";
    nameSpan.textContent = this.currentChannelName === "Chat Demo" ? "Chat Demo" : `#${this.currentChannelName}`;
    const membersBtn = document.createElement("button");
    membersBtn.textContent = "\u{1F465}";
    membersBtn.title = "Members";
    membersBtn.setAttribute("aria-expanded", "false");
    this.headerBar.append(menuBtn, nameSpan, membersBtn);
    this.chatAreaSlot.insertBefore(this.headerBar, this.chatAreaSlot.firstChild);

    this.backdrop = document.createElement("div");
    this.backdrop.className = "responsive-backdrop";
    this.container.appendChild(this.backdrop);

    const signal = this.modeAbort!.signal;

    menuBtn.addEventListener("click", () => {
      if (this.activeDrawer === "channel") this.closeDrawer();
      else this.openDrawer("channel");
    }, { signal });

    membersBtn.addEventListener("click", () => {
      if (this.activeDrawer === "member") this.closeDrawer();
      else this.openDrawer("member");
    }, { signal });

    this.backdrop.addEventListener("click", () => this.closeDrawer(), { signal });

    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape" && this.activeDrawer) this.closeDrawer();
    }, { signal });
  }

  private openDrawer(side: "channel" | "member"): void {
    if (this.activeDrawer) this.closeDrawer();
    this.activeDrawer = side;

    const drawerSlot = side === "channel" ? this.channelSlot : this.memberSlot;
    const oppositeSlot = side === "channel" ? this.memberSlot : this.channelSlot;
    drawerSlot?.classList.add("open");
    drawerSlot?.setAttribute("aria-hidden", "false");
    this.backdrop?.classList.add("visible");

    this.chatAreaEl?.setAttribute("inert", "");
    oppositeSlot?.setAttribute("inert", "");

    const buttons = this.headerBar?.querySelectorAll("button");
    if (buttons) {
      const btnIndex = side === "channel" ? 0 : 1;
      buttons[btnIndex]?.setAttribute("aria-expanded", "true");
    }

    drawerSlot?.focus();
  }

  private closeDrawer(): void {
    if (!this.activeDrawer) return;
    const side = this.activeDrawer;
    this.activeDrawer = null;

    const drawerSlot = side === "channel" ? this.channelSlot : this.memberSlot;
    drawerSlot?.classList.remove("open");
    drawerSlot?.setAttribute("aria-hidden", "true");
    this.backdrop?.classList.remove("visible");

    this.chatAreaEl?.removeAttribute("inert");
    this.channelSlot?.removeAttribute("inert");
    this.memberSlot?.removeAttribute("inert");

    const buttons = this.headerBar?.querySelectorAll("button");
    if (buttons) {
      buttons[0]?.setAttribute("aria-expanded", "false");
      buttons[1]?.setAttribute("aria-expanded", "false");

      const btnIndex = side === "channel" ? 0 : 1;
      buttons[btnIndex]?.focus();
    }
  }

  private setupTablet(): void {
    if (!this.channelSlot || !this.memberSlot || !this.chatAreaSlot) return;

    this.chatAreaSlot.style.flex = "75";

    this.tabSwitcher = document.createElement("div");
    this.tabSwitcher.className = "responsive-tabs";
    const channelsTab = document.createElement("button");
    channelsTab.textContent = "Channels";
    channelsTab.dataset.tab = "channels";
    const membersTab = document.createElement("button");
    membersTab.textContent = "Members";
    membersTab.dataset.tab = "members";
    this.tabSwitcher.append(channelsTab, membersTab);

    this.switchTabTo(this.tabletActiveTab);

    const signal = this.modeAbort!.signal;
    channelsTab.addEventListener("click", () => this.switchTab("channels"), { signal });
    membersTab.addEventListener("click", () => this.switchTab("members"), { signal });
  }

  private switchTab(tab: "channels" | "members"): void {
    this.tabletActiveTab = tab;
    this.switchTabTo(tab);
  }

  private switchTabTo(tab: "channels" | "members"): void {
    if (!this.channelSlot || !this.memberSlot || !this.tabSwitcher) return;

    if (tab === "channels") {
      this.channelSlot.style.display = "";
      this.channelSlot.style.flex = "25";
      this.channelSlot.classList.add("sidebar-with-tabs");
      this.memberSlot.style.display = "none";
      this.memberSlot.style.order = "";
      this.memberSlot.classList.remove("sidebar-with-tabs");
      this.channelSlot.insertBefore(this.tabSwitcher, this.channelSlot.firstChild);
    } else {
      this.channelSlot.style.display = "none";
      this.channelSlot.classList.remove("sidebar-with-tabs");
      this.memberSlot.style.display = "";
      this.memberSlot.style.flex = "25";
      this.memberSlot.style.order = "-1";
      this.memberSlot.classList.add("sidebar-with-tabs");
      this.memberSlot.insertBefore(this.tabSwitcher, this.memberSlot.firstChild);
    }

    const buttons = this.tabSwitcher.querySelectorAll("button");
    buttons.forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.tab === tab);
    });
  }

  private setupDesktop(): void {
    if (this.channelSlot) this.channelSlot.style.display = "";
    if (this.memberSlot) {
      this.memberSlot.style.display = "";
      this.memberSlot.style.order = "";
    }
  }
}
