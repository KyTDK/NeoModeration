#!/usr/bin/env node
/**
 * modrinth-publish.mjs — drive Modrinth in the already-logged-in CDP Chrome
 * (the same real-Chrome debug session the Spigot tool uses), then publish via
 * the documented REST API.
 *
 * USAGE
 *   node scripts/modrinth-publish.mjs open              # open sign-in page, bring to front
 *   node scripts/modrinth-publish.mjs check             # is the browser signed in to Modrinth?
 *   node scripts/modrinth-publish.mjs inspect           # inspect the create-PAT form
 *   node scripts/modrinth-publish.mjs patdebug          # inspect create-PAT state
 *   node scripts/modrinth-publish.mjs patdom            # inspect create-PAT DOM controls
 *   node scripts/modrinth-publish.mjs publish <jar> <version>
 *   node scripts/modrinth-publish.mjs promote <jar> <version>
 *
 * ENV
 *   MODRINTH_CDP_PORT  CDP port of the logged-in Chrome (default 9223)
 *   MODRINTH_TOKEN     bearer token required for API publishing
 */
import { existsSync, readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import path from "node:path";
import { pathToFileURL } from "node:url";

const PORT = process.env.MODRINTH_CDP_PORT || "9223";
const API = "https://api.modrinth.com/v2";

function resolvePlaywright() {
  const candidates = [
    "/Users/kyantaber/Documents/GitHub/neomechanical-platform/node_modules/playwright/index.mjs",
    path.resolve(process.cwd(), "node_modules/playwright/index.mjs"),
  ];
  const found = candidates.find(existsSync);
  if (!found) throw new Error("Could not find Playwright. Set it up like the Spigot tool.");
  return found;
}

async function connect() {
  const { chromium } = await import(`file://${resolvePlaywright()}`);
  const browser = await chromium.connectOverCDP(`http://127.0.0.1:${PORT}`);
  const ctx = browser.contexts()[0];
  if (!ctx) throw new Error(`No browser context on CDP ${PORT}. Is the debug Chrome running?`);
  return { browser, ctx };
}

async function modrinthPage(ctx, url) {
  let page = ctx.pages().find((p) => p.url().includes("modrinth.com"));
  if (!page) page = await ctx.newPage();
  if (url) await page.goto(url, { waitUntil: "domcontentloaded" });
  await page.bringToFront();
  return page;
}

/** Reads the signed-in user from Modrinth's session cookie via the frontend API. */
async function loginInfo(page) {
  return page.evaluate(async () => {
    try {
      const res = await fetch("/api/v1/user", { credentials: "include" }).catch(() => null);
      if (res && res.ok) return await res.json();
    } catch (e) {}
    // Fallback: look for an avatar / username in the nav.
    const el = document.querySelector("[href^='/user/'] , .username, a.user");
    return el ? { username: (el.getAttribute("href") || el.textContent || "").replace("/user/", "").trim() } : null;
  });
}

async function cmdOpen() {
  const { browser, ctx } = await connect();
  try {
    await modrinthPage(ctx, "https://modrinth.com/auth/sign-in");
    console.log("Opened Modrinth sign-in in the debug Chrome. Sign in there, then run: check");
  } finally {
    await browser.close();
  }
}

async function cmdCheck() {
  const { browser, ctx } = await connect();
  try {
    const page = await modrinthPage(ctx, "https://modrinth.com/settings/account");
    await page.waitForTimeout(1500);
    const info = await loginInfo(page);
    console.log(JSON.stringify({ loggedIn: !!info, user: info?.username || info?.email || null }));
  } finally {
    await browser.close();
  }
}

/** Read-only: dump the create-PAT form structure so automation targets the right controls. */
async function cmdInspect() {
  const { browser, ctx } = await connect();
  try {
    const page = await modrinthPage(ctx, "https://modrinth.com/settings/pats");
    await page.waitForTimeout(1500);
    // Reveal the create form (robust: role-based, then any button containing the text).
    try {
      await page.getByRole("button", { name: /create a pat/i }).first().click({ timeout: 4000 });
    } catch {
      try { await page.locator("button:has-text('Create a PAT')").first().click({ timeout: 4000 }); } catch {}
    }
    await page.waitForTimeout(1800);
    const structure = await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll("button, a.btn, a[role=button]"))
        .map((b) => (b.innerText || b.textContent || "").trim())
        .filter(Boolean).slice(0, 40);
      const inputs = Array.from(document.querySelectorAll("input")).map((i) => ({
        type: i.type, name: i.name, placeholder: i.placeholder, id: i.id,
        labelText: (i.closest("label")?.innerText || document.querySelector(`label[for='${i.id}']`)?.innerText || "").trim().slice(0, 40),
      }));
      const labels = Array.from(document.querySelectorAll("label")).map((l) => (l.innerText || "").trim()).filter(Boolean).slice(0, 60);
      const dialogs = Array.from(document.querySelectorAll("[role=dialog], dialog, .modal")).length;
      const url = location.href;
      return { url, dialogs, buttons: btns, inputs, labels };
    });
    console.log(JSON.stringify(structure, null, 2));
  } finally {
    await browser.close();
  }
}

const PUBLISH_SCOPES = ["Create projects", "Read projects", "Write projects",
  "Create versions", "Read versions", "Write versions"];

async function cmdPatDom() {
  const { browser, ctx } = await connect();
  try {
    const page = await modrinthPage(ctx, "https://modrinth.com/settings/pats");
    await page.waitForTimeout(1200);
    try { await page.getByRole("button", { name: /create a pat/i }).first().click({ timeout: 5000 }); } catch {}
    await page.waitForTimeout(1500);
    const html = await page.evaluate(() => {
      const dlg = document.querySelector("[role=dialog], dialog, .modal, .universal-modal, .content");
      // Find the scope for one toggle + the expires area.
      const scopeBtn = Array.from(document.querySelectorAll("button")).find((b) => /^Create projects$/i.test((b.innerText || "").trim()));
      const expiresLabel = Array.from(document.querySelectorAll("label")).find((l) => /expires/i.test(l.innerText || ""));
      const expiresBox = expiresLabel ? expiresLabel.closest("div") : null;
      return {
        scopeBtnHTML: scopeBtn ? scopeBtn.outerHTML.slice(0, 400) : "none",
        scopeParentHTML: scopeBtn && scopeBtn.parentElement ? scopeBtn.parentElement.outerHTML.slice(0, 300) : "none",
        expiresHTML: expiresBox ? expiresBox.outerHTML.slice(0, 900) : "none",
      };
    });
    console.log(JSON.stringify(html, null, 2));
  } finally {
    await browser.close();
  }
}

async function cmdPatDebug() {
  const { browser, ctx } = await connect();
  try {
    const page = await modrinthPage(ctx, "https://modrinth.com/settings/pats");
    await page.waitForTimeout(1200);
    try { await page.getByRole("button", { name: /create a pat/i }).first().click({ timeout: 5000 }); } catch {}
    await page.waitForTimeout(1200);
    await page.fill("#pat-name", "NeoModeration publishing").catch(() => {});
    for (const scope of PUBLISH_SCOPES) {
      await page.getByRole("button", { name: new RegExp(`^${scope}$`, "i") }).first().click({ timeout: 3000 }).catch(() => {});
    }
    const state = await page.evaluate(() => {
      const createBtn = Array.from(document.querySelectorAll("button")).find((b) => /^Create PAT$/i.test((b.innerText || "").trim()));
      const pressed = Array.from(document.querySelectorAll("button[aria-pressed='true'], button.checked, button[data-selected='true']")).map((b) => (b.innerText || "").trim());
      const dateInputs = Array.from(document.querySelectorAll("input")).map((i) => ({ id: i.id, ph: i.placeholder, type: i.type, val: i.value, disabled: i.disabled }));
      return {
        createDisabled: createBtn ? createBtn.disabled || createBtn.getAttribute("aria-disabled") : "no-btn",
        pressedScopes: pressed,
        dateInputs,
      };
    });
    console.log(JSON.stringify(state, null, 2));
  } finally {
    await browser.close();
  }
}

async function api(token, path, opts = {}) {
  const res = await fetch(`${API}${path}`, {
    ...opts,
    headers: { Authorization: token, "User-Agent": "KyTDK/NeoModeration/publish", ...(opts.headers || {}) },
  });
  return res;
}

async function releaseGameVersions() {
  const res = await fetch(`${API}/tag/game_version`);
  const all = await res.json();
  return all
    .filter((v) => v.version_type === "release" && /^1\.(1[3-9]|2\d)(\.\d+)?$/.test(v.version))
    .map((v) => v.version);
}

export function classifyModrinthProjectLookup(status) {
  if (status === 200) return "exists";
  if (status === 404) return "missing";
  throw new Error(`lookup Modrinth project HTTP ${status}`);
}

export function expectedModrinthJarName(version) {
  return `NeoModeration-${version}-modrinth.jar`;
}

export function buildModrinthVersionData({ projectId, version, changelog, gameVersions, loaders }) {
  return {
    project_id: projectId,
    file_parts: ["file"],
    version_number: version,
    name: `NeoModeration ${version}`,
    changelog,
    dependencies: [],
    game_versions: gameVersions,
    version_type: "release",
    loaders,
    featured: true,
    status: "draft",
  };
}

export function validateModrinthDraftVersion(created, projectId, version) {
  if (!created?.id) throw new Error("Modrinth create-version response omitted the version id.");
  if (created.project_id !== projectId || created.version_number !== version) {
    throw new Error(`Modrinth create-version response did not match the requested version ${version}.`);
  }
  if (created.status !== "draft") {
    throw new Error(`Modrinth create-version response was not draft (status: ${created.status || "missing"}).`);
  }
  return created;
}

export function selectModrinthDraftForPromotion(versions, projectId, version, expectedSha512) {
  const matches = (versions || []).filter(
    (candidate) => candidate.project_id === projectId && candidate.version_number === version,
  );
  if (matches.length !== 1) {
    throw new Error(`Expected exactly one Modrinth draft for version ${version}; found ${matches.length}.`);
  }
  const selected = matches[0];
  if (selected.status !== "draft") {
    throw new Error(`Modrinth version ${version} is not draft (status: ${selected.status || "missing"}).`);
  }
  const primary = selected.files?.find((file) => file.primary) || selected.files?.[0];
  if (primary?.hashes?.sha512 !== expectedSha512) {
    throw new Error(`Modrinth version ${version} SHA-512 does not match the inspected local artifact.`);
  }
  return selected;
}

async function cmdPublish(jar, version) {
  if (!jar || !version || !existsSync(jar)) throw new Error("usage: publish <jar> <version> (jar must exist)");
  const expectedJar = expectedModrinthJarName(version);
  if (path.basename(jar) !== expectedJar) {
    throw new Error(`Modrinth requires the non-obfuscated ${expectedJar} built with mvn -Pmodrinth clean verify.`);
  }
  const token = process.env.MODRINTH_TOKEN;
  if (!token) throw new Error("Set MODRINTH_TOKEN in env.");
  console.log("Publishing with the supplied scoped Modrinth token...");

  const gameVersions = await releaseGameVersions();
  const summary = "Automatic chat moderation for Minecraft — blocks swearing, spam, links, and inappropriate map art. Local rules by default, optional cloud moderation.";
  const body = readFileSync("docs/modrinth-body.md", "utf8");
  const loaders = ["bukkit", "spigot", "paper", "purpur", "folia"];

  const projectResponse = await api(token, "/project/neomoderation");
  const lookup = classifyModrinthProjectLookup(projectResponse.status);
  const jarBlob = new Blob([readFileSync(jar)], { type: "application/java-archive" });

  let projectId;
  if (lookup === "missing") {
    // Step 1: create the project as a draft, metadata only (no versions).
    const data = {
      slug: "neomoderation", title: "NeoModeration", description: summary, body,
      categories: ["management", "social", "utility"], additional_categories: [],
      client_side: "unsupported", server_side: "required", project_type: "plugin",
      license_id: "LicenseRef-All-Rights-Reserved",
      issues_url: "https://github.com/KyTDK/NeoModeration/issues",
      source_url: "https://github.com/KyTDK/NeoModeration",
      is_draft: true, initial_versions: [],
    };
    const fd = new FormData();
    fd.append("data", JSON.stringify(data));
    const res = await api(token, "/project", { method: "POST", body: fd });
    const text = await res.text();
    if (res.status >= 300) throw new Error(`create project HTTP ${res.status}: ${text.slice(0, 400)}`);
    projectId = JSON.parse(text).id;
    console.log(`Created draft project ${projectId}. Uploading version...`);
  } else {
    projectId = (await projectResponse.json()).id;
    console.log(`Project exists (${projectId}). Uploading version...`);
  }

  // Step 2: add the version (no environment fields — plugin loaders don't take them).
  const vdata = buildModrinthVersionData({
    projectId, version, changelog: body, gameVersions, loaders,
  });
  const vfd = new FormData();
  vfd.append("data", JSON.stringify(vdata));
  vfd.append("file", jarBlob, `NeoModeration-${version}.jar`);
  const vres = await api(token, "/version", { method: "POST", body: vfd });
  const vtext = await vres.text();
  if (vres.status >= 300) throw new Error(`add version HTTP ${vres.status}: ${vtext.slice(0, 400)}`);
  let created;
  try {
    created = JSON.parse(vtext);
  } catch {
    throw new Error(`add version returned invalid JSON: ${vtext.slice(0, 400)}`);
  }
  validateModrinthDraftVersion(created, projectId, version);
  console.log(`Uploaded version ${version} as a draft.`);
  console.log("Draft: https://modrinth.com/plugin/neomoderation/settings  (submit for review when ready)");
}

async function cmdPromote(jar, version) {
  if (!jar || !version || !existsSync(jar)) throw new Error("usage: promote <jar> <version> (jar must exist)");
  const expectedJar = expectedModrinthJarName(version);
  if (path.basename(jar) !== expectedJar) {
    throw new Error(`Modrinth requires the non-obfuscated ${expectedJar} built with mvn -Pmodrinth clean verify.`);
  }
  const token = process.env.MODRINTH_TOKEN;
  if (!token) throw new Error("Set MODRINTH_TOKEN in env.");

  const projectResponse = await api(token, "/project/neomoderation");
  const lookup = classifyModrinthProjectLookup(projectResponse.status);
  if (lookup !== "exists") throw new Error("NeoModeration must exist on Modrinth before promoting a version.");
  const projectId = (await projectResponse.json()).id;

  const expectedSha512 = createHash("sha512").update(readFileSync(jar)).digest("hex");
  const draftResponse = await api(token, `/version_file/${expectedSha512}?algorithm=sha512`);
  const draftText = await draftResponse.text();
  if (draftResponse.status !== 200) {
    throw new Error(`lookup draft by SHA-512 HTTP ${draftResponse.status}: ${draftText.slice(0, 400)}`);
  }
  const selected = selectModrinthDraftForPromotion(
    [JSON.parse(draftText)], projectId, version, expectedSha512,
  );

  const promoted = await api(token, `/version/${selected.id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ status: "listed" }),
  });
  const promotedText = await promoted.text();
  if (promoted.status !== 204) {
    throw new Error(`promote version HTTP ${promoted.status}: ${promotedText.slice(0, 400)}`);
  }

  const verify = await api(token, `/version/${selected.id}`);
  const verified = await verify.json().catch(() => null);
  const primary = verified?.files?.find((file) => file.primary) || verified?.files?.[0];
  if (verify.status !== 200 || verified?.status !== "listed"
      || verified?.version_number !== version || primary?.hashes?.sha512 !== expectedSha512) {
    throw new Error(`Modrinth did not confirm the exact listed version ${version}.`);
  }
  console.log(`Promoted exact Modrinth version ${version} from draft to listed for project review.`);
}

async function main() {
  const [cmd, ...args] = process.argv.slice(2);
  try {
    if (cmd === "open") await cmdOpen();
    else if (cmd === "check") await cmdCheck();
    else if (cmd === "inspect") await cmdInspect();
    else if (cmd === "patdebug") await cmdPatDebug();
    else if (cmd === "patdom") await cmdPatDom();
    else if (cmd === "publish") await cmdPublish(args[0], args[1]);
    else if (cmd === "promote") await cmdPromote(args[0], args[1]);
    else {
      console.log("commands: open | check | inspect | patdebug | patdom | publish <jar> <version> | promote <jar> <version>");
      process.exitCode = 1;
    }
  } catch (e) {
    console.error("ERROR:", e.message);
    process.exitCode = 1;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) await main();
