#!/usr/bin/env node
/**
 * hangar-upload-version.mjs
 * ─────────────────────────
 * Publishes a NeoModeration version to Hangar (hangar.papermc.io) by driving the
 * logged-in debug Chrome over CDP. Uses Hangar's "Provide a URL" artifact option,
 * pointing the download at the public GitHub release jar — this avoids Hangar's
 * custom file-dropzone (which reads a file client-side but does not reliably
 * upload it when the input is set programmatically).
 *
 * PREREQUISITES
 *   - Debug Chrome on --remote-debugging-port=9223, signed in to Hangar as owner.
 *   - The Hangar project already exists (hangar.papermc.io/<owner>/<project>).
 *   - The GitHub release <version> exists with the jar asset attached & public.
 *
 * USAGE
 *   node scripts/hangar-upload-version.mjs <version>
 *   e.g. node scripts/hangar-upload-version.mjs 1.4.1
 */
import { Page, sleep } from "./cdp-lib.mjs";
import { isExactHangarVersionUrl } from "./hangar-publish.mjs";
import { hangarPaperVersions } from "./release-compatibility.mjs";

const OWNER = process.env.HANGAR_OWNER || "KyTDK";
const PROJECT = process.env.HANGAR_PROJECT || "NeoModeration";
const GH_REPO = process.env.GH_REPO || "KyTDK/NeoModeration";
const SHOT = process.env.HANGAR_SHOT || "/tmp/hangar-upload.png";

const version = process.argv[2];
if (!version) { console.error("usage: hangar-upload-version.mjs <version>"); process.exit(1); }
const jarUrl = `https://github.com/${GH_REPO}/releases/download/v${version}/${PROJECT}-${version}.jar`;
const changelog = `NeoModeration ${version} - see the full release notes at https://github.com/${GH_REPO}/releases/tag/v${version}`;

async function supportedPaperVersions() {
  const response = await fetch("https://hangar.papermc.io/api/v1/platforms/PAPER/versions");
  if (!response.ok) throw new Error(`lookup Hangar Paper versions HTTP ${response.status}`);
  const versions = hangarPaperVersions(await response.json());
  if (!versions.length) {
    throw new Error("Hangar returned no Paper versions in the verified 1.18.2-1.21.x range.");
  }
  return versions;
}

const p = await Page.attach("hangar.papermc.io");
const bottom = () => p.eval(`window.scrollTo(0, document.body.scrollHeight)`);

/**
 * Robustly type into a Vue-controlled input identified by CSS selector: focus,
 * insert text as real keystrokes, verify the value stuck, and retry (with a
 * coordinate click) until it does. Returns the exact final value.
 */
async function typeInto(selector, text, tries = 4) {
  let lastValue = "";
  for (let i = 0; i < tries; i++) {
    const coords = await p.eval(`(function(){
      var el=document.querySelector(${JSON.stringify(selector)});
      if(!el) return null; el.scrollIntoView({block:'center'}); el.focus();
      var b=el.getBoundingClientRect(); return {x:Math.round(b.x+Math.min(60,b.width/2)), y:Math.round(b.y+b.height/2)};
    })()`);
    if (!coords) { await sleep(600); continue; }
    if (i > 0) { await p.clickAt(coords.x, coords.y); await sleep(200); }
    await p.eval(`(function(){
      var el=document.querySelector(${JSON.stringify(selector)});
      if(!el) return;
      var set=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;
      set.call(el, '');
      el.dispatchEvent(new Event('input',{bubbles:true}));
      el.dispatchEvent(new Event('change',{bubbles:true}));
      el.focus();
    })()`);
    await sleep(200);
    await p.send("Input.insertText", { text });
    await sleep(400);
    lastValue = await p.eval(`(function(){ var el=document.querySelector(${JSON.stringify(selector)}); return el?el.value:''; })()`);
    if (lastValue === text) return lastValue;
  }
  return lastValue;
}
async function clickBtn(reSrc, minY = 0) {
  const b = await p.boxOfExpr(`Array.prototype.slice.call(document.querySelectorAll('button')).find(function(x){return ${reSrc}.test((x.innerText||'').trim()) && !x.disabled && x.getBoundingClientRect().y >= ${minY};})`);
  if (b) { await p.clickAt(b.x, b.y); return true; }
  return false;
}

try {
  // ── Step 1: Artifact — provide the GitHub jar URL + Paper platform ─────────
  await p.navigate(`https://hangar.papermc.io/${OWNER}/${PROJECT}/versions/new`);
  await sleep(2500);
  // Switch the Download to "Provide a URL" (the artifact becomes input[name="url"]).
  await p.eval(`(function(){ var b=Array.prototype.slice.call(document.querySelectorAll('button,a,div,span')).find(function(x){return /^Provide a URL$/i.test((x.innerText||'').trim());}); if(b) b.click(); })()`);
  await sleep(1500);
  // The URL field is Vue-controlled: the native value setter is ignored. Focus it
  // directly (a coordinate click can miss) and type via Input.insertText.
  const urlValue = await typeInto('input[name="url"]', jarUrl);
  const urlLen = urlValue.length;
  if (!urlLen) throw new Error("Hangar external URL field was not found or remained empty.");
  if (urlValue !== jarUrl) throw new Error(`Hangar external URL did not match the requested URL (length ${urlLen}).`);
  const urlSet = true;
  await p.eval(`(function(){ var c=document.querySelector('input[name="Paper-0"]'); if(c && !c.checked) c.click(); })()`);
  await sleep(1000);
  await bottom(); await sleep(400);
  const artifactNext = await clickBtn("/^Next$/i");
  if (!artifactNext) throw new Error("Hangar Next button was not found for the artifact step.");
  await sleep(2500);

  // ── Step 2 Artifact Data: type the version number (Vue field → real keystrokes) ─
  const versionValue = await typeInto('input[name="version"]', version);
  const verLen = versionValue.length;
  if (!verLen) throw new Error("Hangar version field was not found or remained empty.");
  if (versionValue !== version) throw new Error(`Hangar version field did not match requested version ${version}.`);
  await bottom(); await sleep(400);
  const artifactDataNext = await clickBtn("/^Next$/i");
  if (!artifactDataNext) throw new Error("Hangar Next button was not found for the artifact-data step.");
  await sleep(2500);

  // ── Step 3 Dependencies: select only the verified 1.18.2–1.21.x range ──────
  const paperVersions = await supportedPaperVersions();
  const availablePaperVersions = await p.eval(`Array.prototype.slice.call(
    document.querySelectorAll('.ml-4 input[type=checkbox]')
  ).map(function(e){ return e.value; })`);
  const desiredPaperVersions = new Set(paperVersions);
  for (const paperVersion of availablePaperVersions) {
    const shouldBeChecked = desiredPaperVersions.has(paperVersion);
    let finalState = null;
    for (let attempt = 0; attempt < 3; attempt++) {
      const state = await p.eval(`(function(){
        var t=${JSON.stringify(paperVersion)};
        var cb=Array.prototype.slice.call(document.querySelectorAll('.ml-4 input[type=checkbox]')).find(function(e){
          return e.value===t;
        });
        if(!cb) return null;
        cb.scrollIntoView({block:'center'});
        var b=cb.getBoundingClientRect();
        return {checked:cb.checked, x:b.x+b.width/2, y:b.y+b.height/2};
      })()`);
      if (!state) throw new Error(`Hangar Paper dependency ${paperVersion} was not found.`);
      finalState = state.checked;
      if (finalState === shouldBeChecked) break;
      await p.clickAt(state.x, state.y);
      await sleep(120);
    }
    finalState = await p.eval(`(function(){
      var t=${JSON.stringify(paperVersion)};
      var cb=Array.prototype.slice.call(document.querySelectorAll('.ml-4 input[type=checkbox]')).find(function(e){
        return e.value===t;
      });
      return cb ? cb.checked : null;
    })()`);
    if (finalState !== shouldBeChecked) {
      throw new Error(
        `Hangar Paper dependency ${paperVersion} did not retain checked=${shouldBeChecked}.`
      );
    }
  }
  const selectedPaperVersions = await p.eval(`Array.prototype.slice.call(
    document.querySelectorAll('.ml-4 input[type=checkbox]:checked')
  ).map(function(e){ return e.value; }).sort()`);
  const expectedPaperVersions = [...desiredPaperVersions].sort();
  if (JSON.stringify(selectedPaperVersions) !== JSON.stringify(expectedPaperVersions)) {
    throw new Error(
      "Hangar Paper dependency selection did not exactly match the verified compatibility range."
    );
  }
  await sleep(500);
  await bottom(); await sleep(400);
  const dependenciesNext = await clickBtn("/^Next$/i");
  if (!dependenciesNext) throw new Error("Hangar Next button was not found for the dependencies step.");
  await sleep(2500);

  // ── Step 4 Changelog: type into the markdown editor ───────────────────────
  const changelogValue = await p.eval(`(function(){
    var desired=${JSON.stringify(changelog)};
    var host=document.querySelector('.CodeMirror');
    if(host && host.CodeMirror){
      host.scrollIntoView({block:'center'});
      host.CodeMirror.setValue(desired);
      host.CodeMirror.save();
      host.CodeMirror.focus();
      return host.CodeMirror.getValue();
    }
    var textarea=document.querySelector('textarea.text-left, textarea');
    if(!textarea) return '';
    textarea.scrollIntoView({block:'center'});
    var set=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set;
    set.call(textarea, desired);
    textarea.dispatchEvent(new Event('input',{bubbles:true}));
    textarea.dispatchEvent(new Event('change',{bubbles:true}));
    return textarea.value;
  })()`);
  if (changelogValue !== changelog) {
    throw new Error("Hangar changelog editor did not retain the requested release notes.");
  }
  await sleep(1000);

  // ── Submit ────────────────────────────────────────────────────────────────
  await bottom(); await sleep(400);
  const created = await clickBtn("/^Create$/i", 150);
  if (!created) throw new Error("Hangar Create button was not found.");
  let after = "";
  for (let i = 0; i < 40; i++) { await sleep(700); after = await p.eval(`location.href`); if (!after.includes("/versions/new")) break; }
  const published = isExactHangarVersionUrl(after, OWNER, PROJECT, version);
  await sleep(1000);
  await p.screenshot(SHOT).catch(() => {});
  if (!published) throw new Error(`Hangar version creation did not reach the requested version URL; current URL: ${after || "unknown"}`);
  console.log(JSON.stringify({ urlSet, versionSet: true, published, finalUrl: after }, null, 1));
} finally {
  p.close();
}
