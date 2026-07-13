// Enter the emailed Hangar verification code and submit. Usage: node hangar-entercode.mjs <code>
import { Page, sleep } from "./cdp-lib.mjs";

const code = (process.argv[2] || "").trim();
if (!code) { console.log("usage: hangar-entercode.mjs <code>"); process.exit(1); }

const p = await Page.attach("hangar.papermc.io");
await p.navigate("https://hangar.papermc.io/auth/settings/account");
await sleep(1500);

// Fill the code field (the text input that isn't username/email) and click Verify Code.
const filled = await p.eval(`(function(){
  var inputs = Array.prototype.slice.call(document.querySelectorAll('input[type=text]'));
  var field = inputs.find(function(i){ var v=(i.value||''); return v!=='KyTDK' && v.indexOf('@')===-1; });
  if(!field) return 'no-field';
  var set=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;
  set.call(field, ${JSON.stringify(code)});
  field.dispatchEvent(new Event('input',{bubbles:true}));
  field.dispatchEvent(new Event('change',{bubbles:true}));
  return 'filled';
})()`);
await sleep(500);
const submitted = await p.eval(`(function(){
  var b=Array.prototype.slice.call(document.querySelectorAll('button')).find(function(x){return /verify code/i.test((x.innerText||'').trim());});
  if(b){ b.click(); return true; } return false;
})()`);
await sleep(3000);
const result = await p.eval(`(function(){
  var t=(document.body.innerText||'').toLowerCase();
  var ok = t.indexOf('verified')!==-1 && t.indexOf('not verified')===-1;
  var notice='';
  var els=Array.prototype.slice.call(document.querySelectorAll('[class*=toast i],[role=alert],[class*=notification i]'));
  for(var i=0;i<els.length;i++){var s=(els[i].innerText||'').trim(); if(s) notice+=s+' | ';}
  return JSON.stringify({ filled:'${filled}', submitted:${submitted}, notice:notice.slice(0,180) });
})()`);
console.log(result);
p.close();
