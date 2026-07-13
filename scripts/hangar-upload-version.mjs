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
 *   e.g. node scripts/hangar-upload-version.mjs 1.4.0
 */
import { Page, sleep } from "./cdp-lib.mjs";

const OWNER = process.env.HANGAR_OWNER || "KyTDK";
const PROJECT = process.env.HANGAR_PROJECT || "NeoModeration";
const GH_REPO = process.env.GH_REPO || "KyTDK/NeoModeration";
const MAJORS = ["1.21", "1.20", "1.19", "1.18", "1.17", "1.16", "1.15", "1.14", "1.13"];
const SHOT = process.env.HANGAR_SHOT || "/tmp/hangar-upload.png";

const version = process.argv[2];
if (!version) { console.error("usage: hangar-upload-version.mjs <version>"); process.exit(1); }
const jarUrl = `https://github.com/${GH_REPO}/releases/download/v${version}/${PROJECT}-${version}.jar`;
const changelog = `NeoModeration ${version} - see the full release notes at https://github.com/${GH_REPO}/releases/tag/v${version}`;

const p = await Page.attach("hangar.papermc.io");
const bottom = () => p.eval(`window.scrollTo(0, document.body.scrollHeight)`);

/**
 * Robustly type into a Vue-controlled input identified by CSS selector: focus,
 * insert text as real keystrokes, verify the value stuck, and retry (with a
 * coordinate click) until it does. Returns the final value length.
 */
async function typeInto(selector, text, tries = 4) {
  for (let i = 0; i < tries; i++) {
    const coords = await p.eval(`(function(){
      var el=document.querySelector(${JSON.stringify(selector)});
      if(!el) return null; el.scrollIntoView({block:'center'}); el.focus();
      var b=el.getBoundingClientRect(); return {x:Math.round(b.x+Math.min(60,b.width/2)), y:Math.round(b.y+b.height/2)};
    })()`);
    if (!coords) { await sleep(600); continue; }
    if (i > 0) { await p.clickAt(coords.x, coords.y); await sleep(200); }
    await sleep(200);
    await p.send("Input.insertText", { text });
    await sleep(400);
    const len = await p.eval(`(function(){ var el=document.querySelector(${JSON.stringify(selector)}); return el?el.value.length:0; })()`);
    if (len > 0) return len;
  }
  return 0;
}
async function clickBtn(reSrc, minY = 0) {
  const b = await p.boxOfExpr(`Array.prototype.slice.call(document.querySelectorAll('button')).find(function(x){return ${reSrc}.test((x.innerText||'').trim()) && !x.disabled && x.getBoundingClientRect().y >= ${minY};})`);
  if (b) { await p.clickAt(b.x, b.y); return true; }
  return false;
}

// ── Step 1: Artifact — provide the GitHub jar URL + Paper platform ─────────
await p.navigate(`https://hangar.papermc.io/${OWNER}/${PROJECT}/versions/new`);
await sleep(2500);
// Switch the Download to "Provide a URL" (the artifact becomes input[name="url"]).
await p.eval(`(function(){ var b=Array.prototype.slice.call(document.querySelectorAll('button,a,div,span')).find(function(x){return /^Provide a URL$/i.test((x.innerText||'').trim());}); if(b) b.click(); })()`);
await sleep(1500);
// The URL field is Vue-controlled: the native value setter is ignored. Focus it
// directly (a coordinate click can miss) and type via Input.insertText.
const urlLen = await typeInto('input[name="url"]', jarUrl);
const urlSet = `len:${urlLen}`;
await p.eval(`(function(){ var c=document.querySelector('input[name="Paper-0"]'); if(c && !c.checked) c.click(); })()`);
await sleep(1000);
await bottom(); await sleep(400);
await clickBtn("/^Next$/i");
await sleep(2500);

// ── Step 2 Artifact Data: type the version number (Vue field → real keystrokes) ─
const verLen = await typeInto('input[name="version"]', version);
await bottom(); await sleep(400);
await clickBtn("/^Next$/i");
await sleep(2500);

// ── Step 3 Dependencies: select Paper version families 1.13–1.21 ───────────
for (const m of MAJORS) {
  const box = await p.eval(`(function(){
    var t=${JSON.stringify(m)};
    var label=Array.prototype.slice.call(document.querySelectorAll('span,div,label')).find(function(e){return (e.childNodes.length===1?e.innerText:'').trim()===t;});
    if(!label) return null;
    var row=label.parentElement, cb=null;
    for(var i=0;i<3 && row;i++){ cb=row.querySelector('input[type=checkbox]'); if(cb) break; row=row.parentElement; }
    if(!cb || cb.checked) return null;
    var b=cb.getBoundingClientRect(); return {x:b.x+b.width/2, y:b.y+b.height/2};
  })()`);
  if (box) { await p.clickAt(box.x, box.y); await sleep(200); }
}
await sleep(500);
await bottom(); await sleep(400);
await clickBtn("/^Next$/i");
await sleep(2500);

// ── Step 4 Changelog: type into the markdown editor ───────────────────────
const editorBox = await p.eval(`(function(){
  var el=document.querySelector('.cm-content, .CodeMirror, [contenteditable=true], .ProseMirror, textarea');
  if(!el) return null; el.scrollIntoView({block:'center'});
  var b=el.getBoundingClientRect(); return {x:b.x+Math.min(80,b.width/2), y:b.y+Math.min(40,b.height/2)};
})()`);
if (editorBox) { await p.clickAt(editorBox.x, editorBox.y); await sleep(400); await p.send("Input.insertText", { text: changelog }); await sleep(1000); }

// ── Submit ────────────────────────────────────────────────────────────────
await bottom(); await sleep(400);
await clickBtn("/^Create$/i", 150);
let after = "";
for (let i = 0; i < 40; i++) { await sleep(700); after = await p.eval(`location.href`); if (!after.includes("/versions/new")) break; }
await sleep(1000);
await p.screenshot(SHOT).catch(() => {});
console.log(JSON.stringify({ urlSet, published: !after.includes("/versions/new"), finalUrl: after }, null, 1));
p.close();
