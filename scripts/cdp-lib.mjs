/**
 * Minimal robust Chrome DevTools Protocol client over a single page target.
 * Avoids Playwright's multi-target attach (which hangs on this profile's
 * extension + iframes). Node 18+ globals: WebSocket, fetch.
 */
const PORT = process.env.CDP_PORT || "9223";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export async function findTarget(urlSubstr) {
  const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
  const t = list.find((x) => x.type === "page" && x.url.includes(urlSubstr));
  if (!t) throw new Error(`No tab matching '${urlSubstr}'. Open one first.`);
  return t;
}

export class Page {
  constructor(ws) { this.ws = ws; this._id = 0; this._pending = new Map(); this._events = []; }

  static async attach(urlSubstr) {
    const t = await findTarget(urlSubstr);
    const ws = new WebSocket(t.webSocketDebuggerUrl);
    await new Promise((res, rej) => { ws.addEventListener("open", res); ws.addEventListener("error", rej); });
    const p = new Page(ws);
    ws.addEventListener("message", (e) => {
      const m = JSON.parse(e.data);
      if (m.id && p._pending.has(m.id)) { p._pending.get(m.id)(m); p._pending.delete(m.id); }
      else if (m.method) p._events.push(m);
    });
    await p.send("Page.enable");
    await p.send("Runtime.enable");
    await p.send("DOM.enable");
    return p;
  }

  send(method, params = {}) {
    return new Promise((resolve, reject) => {
      const id = ++this._id;
      const timer = setTimeout(() => { this._pending.delete(id); reject(new Error(`${method} timed out`)); }, 20000);
      this._pending.set(id, (m) => { clearTimeout(timer); m.error ? reject(new Error(m.error.message)) : resolve(m.result); });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }

  async navigate(url) {
    this._events = [];
    await this.send("Page.navigate", { url });
    // Wait for the load event (or timeout), then let SPA settle.
    for (let i = 0; i < 60; i++) {
      if (this._events.some((e) => e.method === "Page.loadEventFired")) break;
      await sleep(250);
    }
    await sleep(1500);
  }

  async eval(expression) {
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        const r = await this.send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true });
        if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text || "eval error");
        return r.result.value;
      } catch (e) {
        if (/context was destroyed|Cannot find context/i.test(e.message) && attempt < 2) { await sleep(800); continue; }
        throw e;
      }
    }
  }

  // Real mouse click at viewport coords (custom Vue widgets often need this).
  async clickAt(x, y) {
    await this.send("Input.dispatchMouseEvent", { type: "mouseMoved", x, y });
    await this.send("Input.dispatchMouseEvent", { type: "mousePressed", x, y, button: "left", clickCount: 1 });
    await this.send("Input.dispatchMouseEvent", { type: "mouseReleased", x, y, button: "left", clickCount: 1 });
  }

  // Center coords of the first element matching a JS finder returning an element.
  async boxOfExpr(finderExpr) {
    const r = await this.eval(`(function(){ var el=(${finderExpr}); if(!el) return null; var b=el.getBoundingClientRect(); return {x:b.x+b.width/2, y:b.y+b.height/2, w:b.width}; })()`);
    return r;
  }

  async setFileInput(selector, filePath) {
    const { root } = await this.send("DOM.getDocument", { depth: 0 });
    const { nodeId } = await this.send("DOM.querySelector", { nodeId: root.nodeId, selector });
    if (!nodeId) throw new Error(`file input not found: ${selector}`);
    await this.send("DOM.setFileInputFiles", { files: [filePath], nodeId });
  }

  async screenshot(path) {
    const r = await this.send("Page.captureScreenshot", { format: "png" });
    const { writeFileSync } = await import("node:fs");
    writeFileSync(path, Buffer.from(r.data, "base64"));
    return path;
  }

  close() { try { this.ws.close(); } catch {} }
}

export { sleep };
