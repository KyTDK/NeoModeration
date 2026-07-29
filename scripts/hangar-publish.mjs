#!/usr/bin/env node
/**
 * hangar-publish.mjs — publish NeoModeration to Hangar (hangar.papermc.io) via API.
 *
 * USAGE
 *   HANGAR_API_KEY=... node scripts/hangar-publish.mjs publish <jar> <version>
 *
 * The API key is created at hangar.papermc.io/auth/settings/api-keys with the
 * create_project and create_version permissions. Passed via env, never printed.
 */
import { existsSync, readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { hangarPaperVersions } from "./release-compatibility.mjs";

const BASE = "https://hangar.papermc.io/api/v1";
const OWNER = process.env.HANGAR_OWNER || "KyTDK";

async function jwt(apiKey) {
  const res = await fetch(`${BASE}/authenticate?apiKey=${encodeURIComponent(apiKey)}`, { method: "POST" });
  if (!res.ok) throw new Error(`authenticate HTTP ${res.status}: ${(await res.text()).slice(0, 200)}`);
  return (await res.json()).token;
}

async function h(token, path, opts = {}) {
  return fetch(`${BASE}${path}`, { ...opts, headers: { Authorization: token, ...(opts.headers || {}) } });
}

async function paperVersions() {
  // Hangar validates exact platform versions against this public catalog.
  const res = await fetch(`${BASE}/platforms/PAPER/versions`);
  if (!res.ok) throw new Error(`lookup Hangar Paper versions HTTP ${res.status}`);
  const versions = hangarPaperVersions(await res.json());
  if (!versions.length) {
    throw new Error("Hangar returned no Paper versions in the verified 1.18.2-1.21.x range.");
  }
  return versions;
}

export function classifyHangarProjectLookup(status) {
  if (status === 200) return "exists";
  if (status === 404) return "missing";
  throw new Error(`lookup Hangar project HTTP ${status}`);
}

export function isExactHangarVersionUrl(url, owner, project, version) {
  try {
    const parsed = new URL(url);
    const path = decodeURIComponent(parsed.pathname).replace(/\/$/, "");
    return parsed.protocol === "https:"
      && parsed.hostname === "hangar.papermc.io"
      && path === `/${owner}/${project}/versions/${version}`;
  } catch {
    return false;
  }
}

async function cmdPublish(jar, version) {
  if (!jar || !version || !existsSync(jar)) throw new Error("usage: publish <jar> <version>");
  const apiKey = process.env.HANGAR_API_KEY;
  if (!apiKey) throw new Error("Set HANGAR_API_KEY in env.");
  const token = await jwt(apiKey);
  console.log("Authenticated with Hangar.");

  // Resolve owner numeric id.
  const userRes = await h(token, `/users/${OWNER}`);
  if (!userRes.ok) throw new Error(`lookup user ${OWNER} HTTP ${userRes.status}`);
  const user = await userRes.json();

  // Ensure the project exists.
  const projectResponse = await h(token, `/projects/${OWNER}/NeoModeration`);
  const projectLookup = classifyHangarProjectLookup(projectResponse.status);
  if (projectLookup === "missing") {
    const form = {
      name: "NeoModeration",
      category: "protection",
      description: "Monitor-first Minecraft chat moderation with local rules, optional cloud checks, and NSFW map-art scanning.",
      pageContent: readFileSync("docs/modrinth-body.md", "utf8"),
      ownerId: user.id,
    };
    const res = await h(token, "/projects", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(form),
    });
    const text = await res.text();
    if (res.status >= 300) throw new Error(`create project HTTP ${res.status}: ${text.slice(0, 400)}`);
    console.log("Created Hangar project.");
  } else {
    console.log("Hangar project already exists.");
  }

  // Determine Paper versions to declare.
  const versions = await paperVersions();

  // Upload the version (multipart: versionUpload JSON + jar file).
  const meta = {
    version,
    pluginDependencies: {},
    platformDependencies: { PAPER: versions },
    description: `NeoModeration ${version}. See https://github.com/KyTDK/NeoModeration/releases/tag/v${version}`,
    files: [{ platforms: ["PAPER"] }],
    channel: "Release",
  };
  const fd = new FormData();
  fd.append("versionUpload", new Blob([JSON.stringify(meta)], { type: "application/json" }));
  fd.append("files", new Blob([readFileSync(jar)], { type: "application/java-archive" }), `NeoModeration-${version}.jar`);
  const up = await h(token, `/projects/${OWNER}/NeoModeration/upload`, { method: "POST", body: fd });
  const upText = await up.text();
  if (up.status >= 300) throw new Error(`upload version HTTP ${up.status}: ${upText.slice(0, 500)}`);
  console.log("Uploaded version:", upText.slice(0, 200));
  console.log("Project: https://hangar.papermc.io/" + OWNER + "/NeoModeration");
}

async function main() {
  const [cmd, ...args] = process.argv.slice(2);
  try {
    if (cmd === "publish") await cmdPublish(args[0], args[1]);
    else { console.log("commands: publish <jar> <version>"); process.exitCode = 1; }
  } catch (e) {
    console.error("ERROR:", e.message);
    process.exitCode = 1;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) await main();
