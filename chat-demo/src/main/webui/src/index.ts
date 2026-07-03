import { loadSite, registerPanel } from "@casehubio/pages-runtime";
import { columns, split, dockBar, hostPanel, withId } from "@casehubio/pages-ui";
import { ResponsiveController } from "./responsive.js";

import "./panels/channel-sidebar.js";
import "./panels/message-list.js";
import "./panels/message-input.js";
import "./panels/member-list.js";

registerPanel("channels", "chat-channel-sidebar");
registerPanel("messages", "chat-message-list");
registerPanel("input", "chat-message-input");
registerPanel("members", "chat-member-list");

const WS_URL = `ws://${window.location.host}/ws/chat`;

const chatApp = columns([0, 1],
  [withId("dock", dockBar("vertical", [
    { icon: "\u{1F4AC}", label: "Channels", panelId: "channel-panel", defaultOpen: true },
    { icon: "\u{1F465}", label: "Members", panelId: "member-panel", defaultOpen: true },
  ]))],
  [withId("main-split", split("horizontal", [
    withId("channel-panel", hostPanel("channels")),
    withId("chat-area", split("vertical", [
      hostPanel("messages"),
      hostPanel("input"),
    ], { ratio: [90, 10] })),
    withId("member-panel", hostPanel("members")),
  ], { ratio: [20, 60, 20] }))],
);

const container = document.getElementById("app");
if (container) {
  loadSite(container, chatApp, {
    providerConfig: {
      webSocket: {},
    },
  }).then((site) => {
    site.setTheme("dark");
    new ResponsiveController(container);

    // Open WebSocket and relay messages to all panels as DOM events
    const ws = new WebSocket(WS_URL);
    ws.addEventListener("message", (event) => {
      try {
        const data = JSON.parse(event.data);
        const messages = Array.isArray(data) ? data : [data];
        for (const msg of messages) {
          document.dispatchEvent(new CustomEvent("pages-event", {
            bubbles: true,
            composed: true,
            detail: { topic: "ws-data", payload: msg },
          }));
        }
      } catch (e) {
        console.warn("Failed to parse WebSocket message:", e);
      }
    });
  }).catch(console.error);
}
