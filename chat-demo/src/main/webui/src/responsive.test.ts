import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { createMockLayout, mockMatchMedia, cleanupDOM, MQ_TABLET, MQ_DESKTOP } from "./test-helpers.js";

// Will fail until ResponsiveController exists
import { ResponsiveController } from "./responsive.js";

describe("ResponsiveController", () => {
  let app: HTMLElement;

  afterEach(() => {
    cleanupDOM();
  });

  describe("CSS injection", () => {
    it("injects a <style> element into document.head on construction", () => {
      mockMatchMedia({ [MQ_DESKTOP]: true, [MQ_TABLET]: false });
      app = createMockLayout();
      const ctrl = new ResponsiveController(app);
      const style = document.head.querySelector("style[data-responsive]");
      expect(style).not.toBeNull();
      expect(style!.textContent).toContain("#app.phone");
      expect(style!.textContent).toContain("#app.tablet");
      ctrl.dispose();
    });

    it("removes the <style> element on dispose", () => {
      mockMatchMedia({ [MQ_DESKTOP]: true, [MQ_TABLET]: false });
      app = createMockLayout();
      const ctrl = new ResponsiveController(app);
      ctrl.dispose();
      const style = document.head.querySelector("style[data-responsive]");
      expect(style).toBeNull();
    });
  });

  describe("mode detection", () => {
    it("sets desktop class when viewport >= 1024px", () => {
      mockMatchMedia({ [MQ_DESKTOP]: true, [MQ_TABLET]: false });
      app = createMockLayout();
      const ctrl = new ResponsiveController(app);
      expect(app.classList.contains("desktop")).toBe(true);
      expect(app.classList.contains("phone")).toBe(false);
      expect(app.classList.contains("tablet")).toBe(false);
      ctrl.dispose();
    });

    it("sets tablet class when viewport 768–1023px", () => {
      mockMatchMedia({ [MQ_DESKTOP]: false, [MQ_TABLET]: true });
      app = createMockLayout();
      const ctrl = new ResponsiveController(app);
      expect(app.classList.contains("tablet")).toBe(true);
      ctrl.dispose();
    });

    it("sets phone class when viewport < 768px", () => {
      mockMatchMedia({ [MQ_DESKTOP]: false, [MQ_TABLET]: false });
      app = createMockLayout();
      const ctrl = new ResponsiveController(app);
      expect(app.classList.contains("phone")).toBe(true);
      ctrl.dispose();
    });
  });

  describe("breakpoint transitions", () => {
    it("switches from desktop to phone on resize", () => {
      const media = mockMatchMedia({ [MQ_DESKTOP]: true, [MQ_TABLET]: false });
      app = createMockLayout();
      const ctrl = new ResponsiveController(app);
      expect(app.classList.contains("desktop")).toBe(true);

      media.setMatches(MQ_DESKTOP, false);
      expect(app.classList.contains("phone")).toBe(true);
      expect(app.classList.contains("desktop")).toBe(false);
      ctrl.dispose();
    });

    it("switches from phone to tablet on resize", () => {
      const media = mockMatchMedia({ [MQ_DESKTOP]: false, [MQ_TABLET]: false });
      app = createMockLayout();
      const ctrl = new ResponsiveController(app);
      expect(app.classList.contains("phone")).toBe(true);

      media.setMatches(MQ_TABLET, true);
      expect(app.classList.contains("tablet")).toBe(true);
      expect(app.classList.contains("phone")).toBe(false);
      ctrl.dispose();
    });
  });

  describe("channel tracking", () => {
    it("stores channelName from channel-selected events", () => {
      mockMatchMedia({ [MQ_DESKTOP]: false, [MQ_TABLET]: false });
      app = createMockLayout();
      const ctrl = new ResponsiveController(app);

      document.dispatchEvent(new CustomEvent("pages-event", {
        bubbles: true,
        composed: true,
        detail: { topic: "channel-selected", payload: { channelId: "ch1", channelName: "general" } },
      }));

      expect((ctrl as unknown as { currentChannelName: string }).currentChannelName).toBe("general");
      ctrl.dispose();
    });
  });

  describe("dispose", () => {
    it("removes mode class from #app", () => {
      mockMatchMedia({ [MQ_DESKTOP]: true, [MQ_TABLET]: false });
      app = createMockLayout();
      const ctrl = new ResponsiveController(app);
      ctrl.dispose();
      expect(app.classList.contains("desktop")).toBe(false);
      expect(app.classList.contains("phone")).toBe(false);
      expect(app.classList.contains("tablet")).toBe(false);
    });

    it("stops responding to breakpoint changes after dispose", () => {
      const media = mockMatchMedia({ [MQ_DESKTOP]: true, [MQ_TABLET]: false });
      app = createMockLayout();
      const ctrl = new ResponsiveController(app);
      ctrl.dispose();

      media.setMatches(MQ_DESKTOP, false);
      expect(app.classList.contains("phone")).toBe(false);
      expect(app.classList.contains("desktop")).toBe(false);
    });
  });

  describe("phone mode", () => {
    let media: ReturnType<typeof mockMatchMedia>;

    beforeEach(() => {
      media = mockMatchMedia({ [MQ_DESKTOP]: false, [MQ_TABLET]: false });
      app = createMockLayout();
    });

    it("injects header bar into chat-area slot", () => {
      const ctrl = new ResponsiveController(app);
      const header = app.querySelector(".responsive-header");
      expect(header).not.toBeNull();
      expect(header!.querySelector(".channel-name")!.textContent).toBe("Chat Demo");
      ctrl.dispose();
    });

    it("injects backdrop into container", () => {
      const ctrl = new ResponsiveController(app);
      expect(app.querySelector(".responsive-backdrop")).not.toBeNull();
      ctrl.dispose();
    });

    it("adds drawer CSS classes to sidebar slots", () => {
      const ctrl = new ResponsiveController(app);
      const channelSlot = app.querySelector('[data-component-id="channel-panel"]')!.closest("[data-slot]")!;
      const memberSlot = app.querySelector('[data-component-id="member-panel"]')!.closest("[data-slot]")!;
      expect(channelSlot.classList.contains("channel-drawer-slot")).toBe(true);
      expect(memberSlot.classList.contains("member-drawer-slot")).toBe(true);
      ctrl.dispose();
    });

    it("opens channel drawer on hamburger click", () => {
      const ctrl = new ResponsiveController(app);
      const menuBtn = app.querySelector(".responsive-header button")!;
      (menuBtn as HTMLElement).click();
      const channelSlot = app.querySelector('[data-component-id="channel-panel"]')!.closest("[data-slot]")!;
      expect(channelSlot.classList.contains("open")).toBe(true);
      expect(app.querySelector(".responsive-backdrop")!.classList.contains("visible")).toBe(true);
      ctrl.dispose();
    });

    it("sets inert on chat-area element (not slot) when drawer opens", () => {
      const ctrl = new ResponsiveController(app);
      const menuBtn = app.querySelector(".responsive-header button")!;
      (menuBtn as HTMLElement).click();
      const chatArea = app.querySelector('[data-component-id="chat-area"]')!;
      expect(chatArea.hasAttribute("inert")).toBe(true);
      const chatAreaSlot = chatArea.closest("[data-slot]")!;
      expect(chatAreaSlot.hasAttribute("inert")).toBe(false);
      ctrl.dispose();
    });

    it("sets inert on opposite drawer slot when drawer opens", () => {
      const ctrl = new ResponsiveController(app);
      const menuBtn = app.querySelector(".responsive-header button")!;
      (menuBtn as HTMLElement).click();
      const memberSlot = app.querySelector('[data-component-id="member-panel"]')!.closest("[data-slot]")!;
      expect(memberSlot.hasAttribute("inert")).toBe(true);
      ctrl.dispose();
    });

    it("closes drawer on backdrop click", () => {
      const ctrl = new ResponsiveController(app);
      const menuBtn = app.querySelector(".responsive-header button")!;
      (menuBtn as HTMLElement).click();
      const backdrop = app.querySelector(".responsive-backdrop")! as HTMLElement;
      backdrop.click();
      const channelSlot = app.querySelector('[data-component-id="channel-panel"]')!.closest("[data-slot]")!;
      expect(channelSlot.classList.contains("open")).toBe(false);
      expect(backdrop.classList.contains("visible")).toBe(false);
      ctrl.dispose();
    });

    it("closes drawer on Escape key", () => {
      const ctrl = new ResponsiveController(app);
      const menuBtn = app.querySelector(".responsive-header button")!;
      (menuBtn as HTMLElement).click();
      document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
      const channelSlot = app.querySelector('[data-component-id="channel-panel"]')!.closest("[data-slot]")!;
      expect(channelSlot.classList.contains("open")).toBe(false);
      ctrl.dispose();
    });

    it("removes inert from all elements when drawer closes", () => {
      const ctrl = new ResponsiveController(app);
      const menuBtn = app.querySelector(".responsive-header button")!;
      (menuBtn as HTMLElement).click();
      document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
      const chatArea = app.querySelector('[data-component-id="chat-area"]')!;
      const memberSlot = app.querySelector('[data-component-id="member-panel"]')!.closest("[data-slot]")!;
      expect(chatArea.hasAttribute("inert")).toBe(false);
      expect(memberSlot.hasAttribute("inert")).toBe(false);
      ctrl.dispose();
    });

    it("sets aria-expanded on toggle buttons", () => {
      const ctrl = new ResponsiveController(app);
      const buttons = app.querySelectorAll(".responsive-header button");
      expect(buttons[0]!.getAttribute("aria-expanded")).toBe("false");

      (buttons[0] as HTMLElement).click();
      expect(buttons[0]!.getAttribute("aria-expanded")).toBe("true");

      document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
      expect(buttons[0]!.getAttribute("aria-expanded")).toBe("false");
      ctrl.dispose();
    });

    it("auto-closes channel drawer on channel selection", () => {
      const ctrl = new ResponsiveController(app);
      const menuBtn = app.querySelector(".responsive-header button")!;
      (menuBtn as HTMLElement).click();

      document.dispatchEvent(new CustomEvent("pages-event", {
        bubbles: true, composed: true,
        detail: { topic: "channel-selected", payload: { channelId: "ch2", channelName: "random" } },
      }));

      const channelSlot = app.querySelector('[data-component-id="channel-panel"]')!.closest("[data-slot]")!;
      expect(channelSlot.classList.contains("open")).toBe(false);
      ctrl.dispose();
    });

    it("updates header channel name on channel selection", () => {
      const ctrl = new ResponsiveController(app);
      document.dispatchEvent(new CustomEvent("pages-event", {
        bubbles: true, composed: true,
        detail: { topic: "channel-selected", payload: { channelId: "ch1", channelName: "general" } },
      }));
      expect(app.querySelector(".channel-name")!.textContent).toBe("#general");
      ctrl.dispose();
    });

    it("uses stored channelName when entering phone mode after desktop selection", () => {
      const desktopMedia = mockMatchMedia({ [MQ_DESKTOP]: true, [MQ_TABLET]: false });
      app = createMockLayout();
      const ctrl = new ResponsiveController(app);

      document.dispatchEvent(new CustomEvent("pages-event", {
        bubbles: true, composed: true,
        detail: { topic: "channel-selected", payload: { channelId: "ch3", channelName: "dev" } },
      }));

      desktopMedia.setMatches(MQ_DESKTOP, false);
      expect(app.querySelector(".channel-name")!.textContent).toBe("#dev");
      ctrl.dispose();
    });

    it("cleans up phone DOM elements on mode transition", () => {
      const ctrl = new ResponsiveController(app);
      expect(app.querySelector(".responsive-header")).not.toBeNull();
      expect(app.querySelector(".responsive-backdrop")).not.toBeNull();

      media.setMatches(MQ_DESKTOP, true);
      expect(app.querySelector(".responsive-header")).toBeNull();
      expect(app.querySelector(".responsive-backdrop")).toBeNull();
      ctrl.dispose();
    });
  });

  describe("tablet mode", () => {
    let media: ReturnType<typeof mockMatchMedia>;

    beforeEach(() => {
      media = mockMatchMedia({ [MQ_DESKTOP]: false, [MQ_TABLET]: true });
      app = createMockLayout();
    });

    it("injects tab switcher into channel-panel slot", () => {
      const ctrl = new ResponsiveController(app);
      const channelSlot = app.querySelector('[data-component-id="channel-panel"]')!.closest("[data-slot]")!;
      expect(channelSlot.querySelector(".responsive-tabs")).not.toBeNull();
      ctrl.dispose();
    });

    it("shows channels tab as active by default", () => {
      const ctrl = new ResponsiveController(app);
      const tabs = app.querySelectorAll(".responsive-tabs button");
      expect(tabs[0]!.classList.contains("active")).toBe(true);
      expect(tabs[1]!.classList.contains("active")).toBe(false);
      ctrl.dispose();
    });

    it("shows channel-panel slot and hides member-panel slot by default", () => {
      const ctrl = new ResponsiveController(app);
      const channelSlot = app.querySelector('[data-component-id="channel-panel"]')!.closest("[data-slot]") as HTMLElement;
      const memberSlot = app.querySelector('[data-component-id="member-panel"]')!.closest("[data-slot]") as HTMLElement;
      expect(channelSlot.style.display).not.toBe("none");
      expect(memberSlot.style.display).toBe("none");
      ctrl.dispose();
    });

    it("switches to members tab on click", () => {
      const ctrl = new ResponsiveController(app);
      const membersTab = app.querySelector('.responsive-tabs button[data-tab="members"]')! as HTMLElement;
      membersTab.click();

      const channelSlot = app.querySelector('[data-component-id="channel-panel"]')!.closest("[data-slot]") as HTMLElement;
      const memberSlot = app.querySelector('[data-component-id="member-panel"]')!.closest("[data-slot]") as HTMLElement;
      expect(channelSlot.style.display).toBe("none");
      expect(memberSlot.style.display).not.toBe("none");
      expect(memberSlot.style.order).toBe("-1");
      ctrl.dispose();
    });

    it("moves tab switcher into member-panel slot when members tab active", () => {
      const ctrl = new ResponsiveController(app);
      const membersTab = app.querySelector('.responsive-tabs button[data-tab="members"]')! as HTMLElement;
      membersTab.click();

      const memberSlot = app.querySelector('[data-component-id="member-panel"]')!.closest("[data-slot]")!;
      expect(memberSlot.querySelector(".responsive-tabs")).not.toBeNull();
      ctrl.dispose();
    });

    it("preserves tabletActiveTab across mode transitions", () => {
      const ctrl = new ResponsiveController(app);
      const membersTab = app.querySelector('.responsive-tabs button[data-tab="members"]')! as HTMLElement;
      membersTab.click();

      media.setMatches(MQ_TABLET, false);
      media.setMatches(MQ_DESKTOP, true);
      media.setMatches(MQ_DESKTOP, false);
      media.setMatches(MQ_TABLET, true);

      const memberSlot = app.querySelector('[data-component-id="member-panel"]')!.closest("[data-slot]") as HTMLElement;
      expect(memberSlot.style.display).not.toBe("none");
      expect(memberSlot.style.order).toBe("-1");

      const tabs = app.querySelectorAll(".responsive-tabs button");
      expect(tabs[1]!.classList.contains("active")).toBe(true);
      ctrl.dispose();
    });

    it("sets chat-area slot flex to 75", () => {
      const ctrl = new ResponsiveController(app);
      const chatAreaSlot = app.querySelector('[data-component-id="chat-area"]')!.closest("[data-slot]") as HTMLElement;
      expect(chatAreaSlot.style.flex).toBe("75 1 0%");
      ctrl.dispose();
    });

    it("cleans up tablet DOM on mode transition", () => {
      const ctrl = new ResponsiveController(app);
      expect(app.querySelector(".responsive-tabs")).not.toBeNull();

      media.setMatches(MQ_TABLET, false);
      media.setMatches(MQ_DESKTOP, true);
      expect(app.querySelector(".responsive-tabs")).toBeNull();
      ctrl.dispose();
    });
  });
});
