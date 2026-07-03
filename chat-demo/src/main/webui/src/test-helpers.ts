export function createMockLayout(): HTMLElement {
  document.body.innerHTML = `
    <div id="app">
      <div data-component-type="columns" style="display: grid; grid-template-columns: 0fr 1fr;">
        <div data-slot="col-0">
          <div data-component-type="dock-bar" data-component-id="dock"></div>
        </div>
        <div data-slot="col-1">
          <div data-component-type="split" data-component-id="main-split" style="display: flex; flex-direction: row;">
            <div data-slot="0" style="flex: 20; overflow: hidden;">
              <div data-component-type="host-panel" data-component-id="channel-panel"></div>
            </div>
            <div data-split-handle="0" style="width: 6px;"></div>
            <div data-slot="1" style="flex: 60; overflow: hidden;">
              <div data-component-type="split" data-component-id="chat-area" style="display: flex; flex-direction: column;">
                <div data-slot="0" style="flex: 90; overflow: hidden;"></div>
                <div data-split-handle="0" style="height: 6px;"></div>
                <div data-slot="1" style="flex: 10; overflow: hidden;"></div>
              </div>
            </div>
            <div data-split-handle="1" style="width: 6px;"></div>
            <div data-slot="2" style="flex: 20; overflow: hidden;">
              <div data-component-type="host-panel" data-component-id="member-panel"></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `;
  return document.getElementById("app")!;
}

export const MQ_TABLET = "(min-width: 768px) and (max-width: 1279px)";
export const MQ_DESKTOP = "(min-width: 1280px)";

interface MockMediaControl {
  setMatches(query: string, matches: boolean): void;
}

export function mockMatchMedia(initial: Record<string, boolean>): MockMediaControl {
  const state = { ...initial };
  const listeners = new Map<string, Set<(e: MediaQueryListEvent) => void>>();

  window.matchMedia = (query: string): MediaQueryList => {
    if (!listeners.has(query)) listeners.set(query, new Set());
    return {
      get matches() {
        return state[query] ?? false;
      },
      media: query,
      addEventListener(type: string, fn: EventListenerOrEventListenerObject, options?: AddEventListenerOptions | boolean) {
        if (type === "change") {
          const handler = fn as (e: MediaQueryListEvent) => void;
          listeners.get(query)!.add(handler);
          if (typeof options === "object" && options.signal) {
            options.signal.addEventListener("abort", () => {
              listeners.get(query)!.delete(handler);
            });
          }
        }
      },
      removeEventListener(type: string, fn: EventListenerOrEventListenerObject) {
        if (type === "change") listeners.get(query)!.delete(fn as (e: MediaQueryListEvent) => void);
      },
      dispatchEvent: () => true,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
    } as MediaQueryList;
  };

  return {
    setMatches(query: string, matches: boolean) {
      state[query] = matches;
      const fns = listeners.get(query);
      if (fns) {
        for (const fn of fns) {
          fn({ matches, media: query } as MediaQueryListEvent);
        }
      }
    },
  };
}

export function cleanupDOM(): void {
  document.body.innerHTML = "";
  document.head.querySelectorAll("style[data-responsive]").forEach((el) => el.remove());
}
