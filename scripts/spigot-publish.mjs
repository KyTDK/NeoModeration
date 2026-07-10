#!/usr/bin/env node
/**
 * spigot-publish.mjs — reliable SpigotMC resource automation for NeoModeration.
 *
 * WHY THIS EXISTS
 *   SpigotMC sits behind Cloudflare and a XenForo login, so a fresh automated
 *   browser cannot get in. The reliable pattern is:
 *     1. Launch real Google Chrome once with a debug port + persistent profile
 *        (scripts/spigot-chrome.sh). You solve Cloudflare + log in ONE time;
 *        the profile keeps the session.
 *     2. Drive that already-logged-in Chrome over CDP with Playwright's
 *        connectOverCDP (NOT agent-browser, which attaches to the wrong context
 *        and misreports login state).
 *
 * USAGE
 *   node scripts/spigot-chrome.sh 9223            # once: log in when it opens
 *   node scripts/spigot-publish.mjs check
 *   node scripts/spigot-publish.mjs describe <bbcode.txt> [--banner <png>]
 *   node scripts/spigot-publish.mjs icon <png>
 *   node scripts/spigot-publish.mjs version <jar> <versionString> <notes.txt>
 *
 * ENV
 *   SPIGOT_CDP_PORT   CDP port of the logged-in Chrome (default 9223)
 *   SPIGOT_RESOURCE   resource slug.id (default neomoderation.136721)
 *   SPIGOT_PW         absolute path to a playwright install (auto-detected)
 */
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

const PORT = process.env.SPIGOT_CDP_PORT || "9223";
const RESOURCE = process.env.SPIGOT_RESOURCE || "neomoderation.136721";
const BASE = `https://www.spigotmc.org/resources/${RESOURCE}`;

function resolvePlaywright() {
  if (process.env.SPIGOT_PW) return process.env.SPIGOT_PW;
  const candidates = [
    "/Users/kyantaber/Documents/GitHub/neomechanical-platform/node_modules/playwright/index.mjs",
    path.resolve(process.cwd(), "node_modules/playwright/index.mjs")
  ];
  const found = candidates.find(existsSync);
  if (!found) throw new Error("Could not find a Playwright install. Set SPIGOT_PW to <path>/playwright/index.mjs");
  return found;
}

async function connect() {
  const { chromium } = await import(`file://${resolvePlaywright()}`);
  const browser = await chromium.connectOverCDP(`http://127.0.0.1:${PORT}`);
  const ctx = browser.contexts()[0];
  if (!ctx) throw new Error(`No browser context on CDP ${PORT}. Run scripts/spigot-chrome.sh ${PORT} first.`);
  const page = ctx.pages().find((p) => p.url().includes("spigotmc.org")) || ctx.pages()[0] || (await ctx.newPage());
  return { browser, page };
}

async function assertLoggedIn(page) {
  await page.goto(BASE, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(1500);
  const state = await page.evaluate(() => ({
    kytdk: /KyTDK/.test((document.body?.innerText || "").slice(0, 900)),
    cloudflare: /Just a moment/i.test(document.title)
  }));
  if (state.cloudflare) throw new Error("Cloudflare challenge is up. Solve it in the Chrome window, then retry.");
  if (!state.kytdk) throw new Error("Not logged in. Log in once in the Chrome window (scripts/spigot-chrome.sh), then retry.");
  return true;
}

/** Set a XenForo redactor field to BBCode by switching it to source mode first. */
async function setRedactorBBCode(page, bbcode, whichSwitch = 0) {
  await page.evaluate((i) => {
    const btns = document.querySelectorAll(".redactor_btn_switchmode a, .redactor_btn_switchmode");
    if (btns[i]) btns[i].click();
  }, whichSwitch);
  await page.waitForTimeout(600);
  const set = await page.evaluate((text) => {
    // After switching, the plain BBCode textarea (name=message / *_html) is editable.
    const tas = Array.from(document.querySelectorAll("textarea")).filter(
      (e) => (e.name === "message" || /message_html|_html$/.test(e.name)) && e.id !== "uix_offCanvasStatus"
    );
    let n = 0;
    for (const ta of tas) {
      ta.value = text;
      ta.dispatchEvent(new Event("input", { bubbles: true }));
      ta.dispatchEvent(new Event("change", { bubbles: true }));
      n++;
    }
    return n;
  }, bbcode);
  return set;
}

async function clickButton(page, labelRe) {
  return page.evaluate((reSrc) => {
    const re = new RegExp(reSrc, "i");
    const b = Array.from(document.querySelectorAll('input[type=submit],button')).find((x) => re.test((x.value || x.innerText || "").trim()));
    if (b) { b.click(); return true; }
    return false;
  }, labelRe.source);
}

async function uploadToFileInput(page, selector, filePath) {
  const input = await page.$(selector);
  if (!input) throw new Error(`file input not found: ${selector}`);
  await input.setInputFiles(filePath);
  await page.waitForTimeout(2500); // XenForo uploads via AJAX on select
}

async function cmdCheck() {
  const { browser, page } = await connect();
  try {
    await assertLoggedIn(page);
    const v = await page.evaluate(() => {
      const t = document.body.innerText;
      const g = (re) => (t.match(re) || [])[1] || "?";
      return { downloads: g(/Total Downloads:\s*([0-9,]+)/i), version: g(/([0-9]+\.[0-9]+\.[0-9]+)/), rating: g(/([0-9]+)\s*ratings/i) };
    });
    console.log("OK logged in.", JSON.stringify(v));
  } finally { await browser.close(); }
}

async function cmdDescribe(file, bannerPng) {
  const bbcode = readFileSync(file, "utf8");
  const { browser, page } = await connect();
  try {
    await assertLoggedIn(page);
    await page.goto(`${BASE}/edit`, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(2500);
    if (/^Error/.test(await page.title())) throw new Error("Edit page returned Error (permission/login).");

    let body = bbcode;
    if (bannerPng) {
      const attachId = await uploadDescriptionImage(page, bannerPng);
      if (attachId) body = `[CENTER][ATTACH=full]${attachId}[/ATTACH][/CENTER]\n\n${bbcode}`;
      else console.warn("banner upload did not return an attachment id; posting text only");
    }
    const n = await setRedactorBBCode(page, body, 0);
    if (!n) throw new Error("could not find the Description textarea to set.");
    const saved = await clickButton(page, /^Save$/);
    await page.waitForTimeout(4000);
    console.log(saved ? `Description saved (fields set: ${n}).` : "Save button not found.");
  } finally { await browser.close(); }
}

/** Upload an image into the description's attachment area and return its attachment id. */
async function uploadDescriptionImage(page, png) {
  const imgInput = await page.$('input[type=file]');
  if (!imgInput) return null;
  await imgInput.setInputFiles(png);
  await page.waitForTimeout(4000);
  const base = path.basename(png);
  return page.evaluate((fileName) => {
    // Match the freshly-uploaded attachment <li> by its filename, then read its id.
    const li = Array.from(document.querySelectorAll("li.AttachedFile, [id^=attachment]")).find((n) =>
      (n.innerText || "").includes(fileName));
    if (li) {
      const link = li.querySelector("[data-attachmentid]");
      if (link) return link.getAttribute("data-attachmentid");
      const m = (li.id || "").match(/(\d+)/);
      if (m) return m[1];
    }
    // Fallback: the last data-attachmentid on the page (newest upload).
    const all = Array.from(document.querySelectorAll("[data-attachmentid]"));
    return all.length ? all[all.length - 1].getAttribute("data-attachmentid") : null;
  }, base);
}

async function cmdIcon(png) {
  const { browser, page } = await connect();
  try {
    await assertLoggedIn(page);
    await page.goto(`${BASE}/icon`, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(2500);
    await uploadToFileInput(page, "#ctrl_icon", png);
    const saved = await clickButton(page, /Save Changes/);
    await page.waitForTimeout(4000);
    console.log(saved ? "Icon saved." : "Save Changes button not found.");
  } finally { await browser.close(); }
}

async function cmdVersion(jar, versionString, notesFile) {
  const notes = readFileSync(notesFile, "utf8");
  const { browser, page } = await connect();
  try {
    await assertLoggedIn(page);
    await page.goto(`${BASE}/add-version`, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(2500);
    await page.evaluate((v) => {
      const vs = document.querySelector("#ctrl_version_string"); if (vs) { vs.value = v; vs.dispatchEvent(new Event("input", { bubbles: true })); }
    }, versionString);
    await uploadToFileInput(page, 'input[type=file]', jar); // "Updated Resource File" is the first file input
    await setRedactorBBCode(page, notes, 0);
    const saved = await clickButton(page, /Save Update/);
    await page.waitForTimeout(5000);
    console.log(saved ? `Posted version ${versionString}.` : "Save Update button not found.");
  } finally { await browser.close(); }
}

const [cmd, ...args] = process.argv.slice(2);
const bannerFlag = args.indexOf("--banner");
const banner = bannerFlag >= 0 ? args[bannerFlag + 1] : null;
const positional = args.filter((a, i) => a !== "--banner" && args[i - 1] !== "--banner");

try {
  if (cmd === "check") await cmdCheck();
  else if (cmd === "describe") await cmdDescribe(positional[0], banner);
  else if (cmd === "icon") await cmdIcon(positional[0]);
  else if (cmd === "version") await cmdVersion(positional[0], positional[1], positional[2]);
  else {
    console.log("commands: check | describe <bbcode.txt> [--banner <png>] | icon <png> | version <jar> <ver> <notes.txt>");
    process.exit(1);
  }
} catch (e) {
  console.error("ERROR:", e.message);
  process.exit(1);
}
