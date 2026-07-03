import { describe, it, expect, afterEach } from "vitest";
import { createMockLayout, mockMatchMedia, cleanupDOM, MQ_TABLET, MQ_DESKTOP } from "./test-helpers.js";
import { ResponsiveController } from "./responsive.js";

function getComputedDimensions(el: HTMLElement) {
  return {
    offsetHeight: el.offsetHeight,
    scrollHeight: el.scrollHeight,
    clientHeight: el.clientHeight,
  };
}

function findOverflow(root: HTMLElement, viewportHeight: number): string[] {
  const violations: string[] = [];

  function walk(el: Element): void {
    if (!(el instanceof HTMLElement)) return;
    const r = el.getBoundingClientRect();
    if (r.height === 0) return;

    if (r.bottom > viewportHeight + 0.5) {
      const id = el.id || el.dataset?.componentId || el.dataset?.slot || el.className || el.tagName;
      violations.push(`${id}: bottom=${r.bottom.toFixed(1)} exceeds viewport=${viewportHeight}`);
    }
    if (r.top < -0.5) {
      const id = el.id || el.dataset?.componentId || el.dataset?.slot || el.className || el.tagName;
      violations.push(`${id}: top=${r.top.toFixed(1)} above viewport`);
    }

    for (const child of el.children) walk(child);
  }

  walk(root);
  return violations;
}

describe("layout fit — no viewport overflow", () => {
  afterEach(() => {
    cleanupDOM();
  });

  it("desktop mode: all elements within viewport bounds", () => {
    mockMatchMedia({ [MQ_DESKTOP]: true, [MQ_TABLET]: false });
    const app = createMockLayout();
    const ctrl = new ResponsiveController(app);

    const appDims = getComputedDimensions(app);
    expect(appDims.scrollHeight).toBeLessThanOrEqual(appDims.clientHeight);

    ctrl.dispose();
  });

  it("phone mode: all elements within viewport bounds", () => {
    mockMatchMedia({ [MQ_DESKTOP]: false, [MQ_TABLET]: false });
    const app = createMockLayout();
    const ctrl = new ResponsiveController(app);

    const appDims = getComputedDimensions(app);
    expect(appDims.scrollHeight).toBeLessThanOrEqual(appDims.clientHeight);

    ctrl.dispose();
  });

  it("tablet mode: all elements within viewport bounds", () => {
    mockMatchMedia({ [MQ_DESKTOP]: false, [MQ_TABLET]: true });
    const app = createMockLayout();
    const ctrl = new ResponsiveController(app);

    const appDims = getComputedDimensions(app);
    expect(appDims.scrollHeight).toBeLessThanOrEqual(appDims.clientHeight);

    ctrl.dispose();
  });

  it("chat-area input slot does not use fixed flex ratio", () => {
    mockMatchMedia({ [MQ_DESKTOP]: true, [MQ_TABLET]: false });
    const app = createMockLayout();
    const ctrl = new ResponsiveController(app);

    const style = document.head.querySelector("style[data-responsive]");
    expect(style).not.toBeNull();
    const css = style!.textContent!;
    expect(css).toContain('[data-component-id="chat-area"] > [data-slot="1"]');
    expect(css).toContain("flex: 0 0 auto");

    ctrl.dispose();
  });

  it("chat-area drag handle is hidden", () => {
    mockMatchMedia({ [MQ_DESKTOP]: true, [MQ_TABLET]: false });
    const app = createMockLayout();
    const ctrl = new ResponsiveController(app);

    const style = document.head.querySelector("style[data-responsive]");
    const css = style!.textContent!;
    expect(css).toContain('[data-component-id="chat-area"] > [data-split-handle]');
    expect(css).toContain("display: none");

    ctrl.dispose();
  });

  it("columns grid fills #app height", () => {
    mockMatchMedia({ [MQ_DESKTOP]: true, [MQ_TABLET]: false });
    const app = createMockLayout();
    const ctrl = new ResponsiveController(app);

    const style = document.head.querySelector("style[data-responsive]");
    const css = style!.textContent!;
    expect(css).toContain('#app > [data-component-type="columns"]');
    expect(css).toContain("height: 100%");

    ctrl.dispose();
  });

  it("slot children fill their parent height", () => {
    mockMatchMedia({ [MQ_DESKTOP]: true, [MQ_TABLET]: false });
    const app = createMockLayout();
    const ctrl = new ResponsiveController(app);

    const style = document.head.querySelector("style[data-responsive]");
    const css = style!.textContent!;
    expect(css).toContain("#app [data-slot] > [data-component-type]");
    expect(css).toContain("height: 100%");
    expect(css).not.toContain("#app [data-slot] > *");

    ctrl.dispose();
  });

  it("phone mode: chat-area-slot uses absolute positioning to prevent flex cross-axis overflow", () => {
    mockMatchMedia({ [MQ_DESKTOP]: false, [MQ_TABLET]: false });
    const app = createMockLayout();
    const ctrl = new ResponsiveController(app);

    const style = document.head.querySelector("style[data-responsive]");
    const css = style!.textContent!;
    expect(css).toContain("#app.phone .chat-area-slot");
    expect(css).toContain("position: absolute !important");
    expect(css).toContain("inset: 0 !important");
    expect(css).toContain("overflow: hidden !important");
    expect(css).toContain('#app.phone [data-component-id="main-split"]');
    expect(css).toContain("position: relative !important");

    ctrl.dispose();
  });

  it("phone mode: chat-area overrides height:100% with flex:1 when header is injected", () => {
    mockMatchMedia({ [MQ_DESKTOP]: false, [MQ_TABLET]: false });
    const app = createMockLayout();
    const ctrl = new ResponsiveController(app);

    const style = document.head.querySelector("style[data-responsive]");
    const css = style!.textContent!;
    expect(css).toContain('#app.phone [data-component-id="chat-area"]');
    expect(css).toContain("height: auto !important");
    expect(css).toContain("flex: 1 !important");

    ctrl.dispose();
  });

  it("tablet mode: sidebar slot with tabs overrides height:100% with flex:1", () => {
    mockMatchMedia({ [MQ_DESKTOP]: false, [MQ_TABLET]: true });
    const app = createMockLayout();
    const ctrl = new ResponsiveController(app);

    const style = document.head.querySelector("style[data-responsive]");
    const css = style!.textContent!;
    expect(css).toContain(".sidebar-with-tabs > [data-component-type]");
    expect(css).toContain("height: auto !important");

    const channelSlot = app.querySelector('[data-component-id="channel-panel"]')!.closest("[data-slot]")!;
    expect(channelSlot.classList.contains("sidebar-with-tabs")).toBe(true);

    ctrl.dispose();
  });

  it("tablet mode: sidebar-with-tabs class moves with active tab", () => {
    mockMatchMedia({ [MQ_DESKTOP]: false, [MQ_TABLET]: true });
    const app = createMockLayout();
    const ctrl = new ResponsiveController(app);

    const channelSlot = app.querySelector('[data-component-id="channel-panel"]')!.closest("[data-slot]")!;
    const memberSlot = app.querySelector('[data-component-id="member-panel"]')!.closest("[data-slot]")!;

    expect(channelSlot.classList.contains("sidebar-with-tabs")).toBe(true);
    expect(memberSlot.classList.contains("sidebar-with-tabs")).toBe(false);

    const membersTab = app.querySelector('.responsive-tabs button[data-tab="members"]')! as HTMLElement;
    membersTab.click();

    expect(channelSlot.classList.contains("sidebar-with-tabs")).toBe(false);
    expect(memberSlot.classList.contains("sidebar-with-tabs")).toBe(true);

    ctrl.dispose();
  });

  it("mode teardown removes sidebar-with-tabs class", () => {
    const media = mockMatchMedia({ [MQ_DESKTOP]: false, [MQ_TABLET]: true });
    const app = createMockLayout();
    const ctrl = new ResponsiveController(app);

    const channelSlot = app.querySelector('[data-component-id="channel-panel"]')!.closest("[data-slot]")!;
    expect(channelSlot.classList.contains("sidebar-with-tabs")).toBe(true);

    media.setMatches(MQ_TABLET, false);
    media.setMatches(MQ_DESKTOP, true);
    expect(channelSlot.classList.contains("sidebar-with-tabs")).toBe(false);

    ctrl.dispose();
  });
});
