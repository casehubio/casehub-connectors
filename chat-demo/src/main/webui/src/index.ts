import { loadSite, registerPanel } from "@casehubio/pages-runtime";
import { columns, split, dockBar, hostPanel, withId, PagesDevAuth, PagesIdentity } from "@casehubio/pages-ui";
import { ResponsiveController } from "./responsive.js";
import { getToken } from "./auth.js";

// Register auth components
customElements.define("pages-dev-auth", PagesDevAuth);
customElements.define("pages-identity", PagesIdentity);

import "./panels/channel-sidebar.js";
import "./panels/message-list.js";
import "./panels/message-input.js";
import "./panels/member-list.js";

registerPanel("channels", "chat-channel-sidebar");
registerPanel("messages", "chat-message-list");
registerPanel("input", "chat-message-input");
registerPanel("members", "chat-member-list");

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
  // Add login gate
  const gate = document.createElement("pages-dev-auth");
  gate.setAttribute("backend-url", "");
  document.body.appendChild(gate);

  loadSite(container, chatApp, {
    providerConfig: {
      webSocket: {},
    },
  }).then((site) => {
    site.setTheme("dark");
    new ResponsiveController(container);

    let ws: WebSocket | null = null;

    function connectWebSocket(): void {
        const token = getToken();
        if (!token) return;
        if (ws) {
            ws.close();
            ws = null;
        }
        const wsProtocol = window.location.protocol === "https:" ? "wss:" : "ws:";
        const wsUrl = `${wsProtocol}//${window.location.host}/ws/chat?token=${encodeURIComponent(token)}`;
        ws = new WebSocket(wsUrl);
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
    }

    // Connect on auth success (login or identity switch)
    document.addEventListener("pages-auth-success", () => {
        connectWebSocket();
    });

    // If already authenticated, connect immediately
    if (getToken()) {
        connectWebSocket();
    }
  }).catch(console.error);
}
