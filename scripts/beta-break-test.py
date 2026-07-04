#!/usr/bin/env python3
"""Adversarial beta-tester suite for NeoModeration 1.1.0.

Tries to break commands, config, offline rules, actions, mute, and cloud path.
Run on the Pterodactyl host against np-test-sandbox (RCON) by default.
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from np_test_rcon import rcon_command  # noqa: E402

CONTAINER = "np-test-sandbox"
LOG = Path("/var/lib/pterodactyl/volumes/np-test-sandbox/logs/latest.log")
CONFIG = Path("/var/lib/pterodactyl/volumes/np-test-sandbox/plugins/NeoModeration/config.yml")
OUT = Path("/tmp/neomod-beta-break.json")
BOT_DIR = Path("/tmp/neomod-mineflayer")
CHAT_BOT = BOT_DIR / "neomod-chat-send.mjs"
MARKER = "beta-break"


@dataclass
class Finding:
    severity: str  # critical | high | medium | low | info
    title: str
    detail: str


@dataclass
class Report:
    findings: list[Finding] = field(default_factory=list)
    checks: list[dict] = field(default_factory=list)

    def check(self, name: str, ok: bool, detail: str = "") -> None:
        self.checks.append({"name": name, "ok": ok, "detail": detail})
        mark = "PASS" if ok else "FAIL"
        print(f"[{mark}] {name}" + (f" — {detail[:220]}" if detail else ""), flush=True)

    def find(self, severity: str, title: str, detail: str) -> None:
        self.findings.append(Finding(severity, title, detail))
        print(f"[FIND:{severity.upper()}] {title} — {detail[:300]}", flush=True)

    def save(self) -> None:
        OUT.write_text(
            json.dumps(
                {
                    "passed": sum(1 for c in self.checks if c["ok"]),
                    "total": len(self.checks),
                    "findings": [f.__dict__ for f in self.findings],
                    "checks": self.checks,
                },
                indent=2,
            ),
            encoding="utf-8",
        )


def strip(text: str) -> str:
    return re.sub(r"§.", "", text or "")


def rcon(cmd: str) -> str:
    return rcon_command(cmd)


def log_since(since: int) -> str:
    if not LOG.exists():
        return ""
    return LOG.read_bytes()[since:].decode("utf-8", errors="replace")


def wait_log(pattern: str, since: int, timeout: float = 35.0) -> tuple[bool, str]:
    deadline = time.time() + timeout
    while time.time() < deadline:
        chunk = log_since(since)
        if re.search(pattern, chunk, re.I):
            return True, chunk
        time.sleep(0.4)
    return False, log_since(since)


def ensure_bot() -> None:
    BOT_DIR.mkdir(parents=True, exist_ok=True)
    if not (BOT_DIR / "node_modules/mineflayer").exists():
        subprocess.run(
            ["npm", "install", "mineflayer", "--prefix", str(BOT_DIR), "--no-save"],
            check=True,
            timeout=180,
        )
    CHAT_BOT.write_text(
        """import mineflayer from 'mineflayer';
const msg = process.argv[2] || 'hello';
const username = process.argv[3] || 'BetaBreak';
let sent = false;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25566, username });
bot.once('spawn', () => {
  setTimeout(() => {
    bot.chat(msg);
    sent = true;
    setTimeout(() => bot.quit(), 2500);
  }, 2000);
});
bot.on('end', () => process.exit(sent ? 0 : 3));
bot.on('error', (e) => { console.error(e.message); process.exit(1); });
setTimeout(() => process.exit(2), 60000);
""",
        encoding="utf-8",
    )


def chat(message: str, username: str) -> None:
    last = None
    for attempt in range(3):
        last = subprocess.run(
            ["node", str(CHAT_BOT), message, username],
            cwd=str(BOT_DIR),
            capture_output=True,
            text=True,
            timeout=70,
        )
        if last.returncode == 0:
            return
        time.sleep(2 + attempt)
    raise RuntimeError(f"chat failed {username}: {last.stdout[-200:] if last else ''} {last.stderr[-200:] if last else ''}")


def restore_baseline() -> None:
    CONFIG.write_text(
        """locale: "en_US"

moderation:
  enabled: true
  offline:
    enabled: true
    blockAnyUrl: false
    normalizeLeetspeak: true
    bannedWords:
      - "badword"
      - "scam"
    bannedUrls:
      - "grabify.link"
      - "discord.gg/free"
  api:
    endpoint: "https://api.neomechanical.com/v1/events"
    apiKey: ""
    connectTimeoutMs: 3000
    readTimeoutMs: 3000
  categories:
    sexual: true
    hate: true
    harassment: true
    violence: true
    scam: true
    spam: true
    illicit: true
    selfHarm: true
  actions:
    - type: CLEAR_CHAT
    - type: MUTE
      durationSeconds: 300
      reason: "Inappropriate chat message"
  chat:
    scanAsyncChat: true
    failOpen: true
""",
        encoding="utf-8",
    )
    rcon("neomod reload")


def main() -> int:
    report = Report()
    ensure_bot()
    restore_baseline()

    # --- Command surface / UX ---
    help_text = strip(rcon("neomod help"))
    report.check("help loads", "neomoderation" in help_text.lower() or "setup" in help_text.lower(), help_text[:160])
    if "moderation.api" in help_text or "config set" in help_text.lower():
        report.find("medium", "Help still exposes technical config paths", help_text[:200])
    if help_text.count("\n") < 3 and "|" in help_text:
        report.find("low", "Help is a flat pipe-separated list (hard to scan)", help_text[:200])
    # Count color-stripped lines that look like bare command dumps
    bare_lines = [ln for ln in help_text.splitlines() if ln.strip().startswith("/")]
    if len(bare_lines) >= 6:
        report.find(
            "medium",
            "Help UX is a wall of bare command lines",
            f"{len(bare_lines)} command-only lines; no grouping/examples",
        )

    # Unknown / garbage commands
    garbage = strip(rcon("neomod asdfghjkl"))
    report.check("unknown subcommand falls back to help", "setup" in garbage.lower() or "neomoderation" in garbage.lower(), garbage[:120])

    # Empty / whitespace args
    empty_setup = strip(rcon("neomod setup"))
    report.check("setup without key shows usage", "usage" in empty_setup.lower() or "apikey" in empty_setup.lower(), empty_setup[:120])
    empty_key = strip(rcon("neomod key"))
    report.check("key without value shows usage", "usage" in empty_key.lower(), empty_key[:120])
    empty_word = strip(rcon("neomod word add"))
    report.check("word add without value rejected", "word" in empty_word.lower() or "add" in empty_word.lower() or "required" in empty_word.lower(), empty_word[:120])

    # Oversized RCON payloads (Minecraft RCON packet limits)
    for key_len in (200, 500, 1000, 2000, 3500, 5000):
        long_key = "nmt_" + ("a" * key_len)
        try:
            long_resp = strip(rcon(f"neomod setup {long_key}"))
            ok = "cloud moderation is on" in long_resp.lower() or "usage" in long_resp.lower()
            report.check(f"API key length {key_len + 4} via RCON", ok, long_resp[:100])
            rcon("neomod key clear")
        except Exception as exc:
            report.find(
                "high" if key_len <= 2000 else "medium",
                f"RCON drops/crashes on API key length {key_len + 4}",
                str(exc),
            )
            report.check(f"API key length {key_len + 4} via RCON", False, str(exc))
            # recover RCON
            time.sleep(1)
            try:
                rcon("neomod status")
            except Exception:
                time.sleep(2)

    for word_len in (100, 500, 1000, 2000):
        long_word = "w" * word_len
        try:
            lw = strip(rcon(f"neomod word add {long_word}"))
            report.check(
                f"banned word length {word_len}",
                "add word" in lw.lower() or "required" in lw.lower(),
                lw[:100],
            )
            try:
                rcon(f"neomod word remove {long_word}")
            except Exception:
                pass
        except Exception as exc:
            report.find("high", f"RCON fails on banned word length {word_len}", str(exc))
            report.check(f"banned word length {word_len}", False, str(exc))
            time.sleep(1)

    # Special characters / injection-ish inputs
    specials = [
        'word"quotes',
        "word'quotes",
        "word\\backslash",
        "word\nnewline",
        "word#comment",
        "word:yaml",
        "word[list]",
        "word{map}",
        "../../../etc/passwd",
        "a" * 1,
    ]
    for special in specials:
        try:
            resp = strip(rcon(f"neomod word add {special}"))
            listed = strip(rcon("neomod word list"))
            # reload must not break plugin
            reload_resp = strip(rcon("neomod reload"))
            ok = "reloaded" in reload_resp.lower()
            report.check(f"special word survives reload: {special[:20]!r}", ok, resp[:80])
            if not ok:
                report.find("high", "Reload broken after special banned word", special)
            rcon(f"neomod word remove {special}")
        except Exception as exc:
            report.find("critical", "Command exception on special word", f"{special!r}: {exc}")
            report.check(f"special word no exception: {special[:20]!r}", False, str(exc))

    # YAML corruption check after specials
    try:
        text = CONFIG.read_text(encoding="utf-8")
        if "\x00" in text:
            report.find("critical", "Null bytes written into config.yml", "binary corruption")
        report.check("config.yml still readable text", "moderation:" in text, f"len={len(text)}")
    except Exception as exc:
        report.find("critical", "config.yml unreadable after special inputs", str(exc))

    # Action command abuse
    restore_baseline()
    rcon("neomod action reset")
    rcon("neomod action add kick")
    rcon("neomod action add ban")
    rcon("neomod action add mute 1s")
    rcon("neomod action add mute 999999999d")
    listed = strip(rcon("neomod action list")).lower()
    # mute should replace previous mute, not duplicate
    mute_count = listed.count("mute")
    report.check("duplicate mute types collapse to one", mute_count == 1, listed)
    if mute_count > 1:
        report.find("high", "Multiple MUTE actions can be stacked", listed)

    bad_dur = strip(rcon("neomod action add mute -5m"))
    report.check("negative mute duration rejected", "bad duration" in bad_dur.lower() or "unknown" in bad_dur.lower() or "usage" in bad_dur.lower(), bad_dur)
    if "on detect" in bad_dur.lower() and "mute" in bad_dur.lower() and "bad" not in bad_dur.lower():
        report.find("high", "Negative mute duration accepted", bad_dur)

    bad_dur2 = strip(rcon("neomod action add mute 0s"))
    report.check("zero mute duration rejected", "bad duration" in bad_dur2.lower() or "positive" in bad_dur2.lower() or "bad" in bad_dur2.lower(), bad_dur2)

    # remove non-existent
    missing = strip(rcon("neomod action remove ban"))
    # ban may exist from earlier; remove twice
    rcon("neomod action remove ban")
    missing2 = strip(rcon("neomod action remove ban"))
    report.check("removing missing action is graceful", "not configured" in missing2.lower() or "missing" in missing2.lower() or "removed" in missing2.lower(), missing2)

    # Empty actions list: chat should still be cancelled?
    CONFIG.write_text(
        CONFIG.read_text(encoding="utf-8").replace(
            "  actions:\n  - type: CLEAR_CHAT\n  - type: MUTE\n    durationSeconds: 300\n    reason: \"Inappropriate chat message\"\n",
            "  actions: []\n",
        ),
        encoding="utf-8",
    )
    rcon("neomod reload")
    rcon("neomod word add betabreakword")
    since = LOG.stat().st_size if LOG.exists() else 0
    chat(f"{MARKER} betabreakword", "BetaEmptyAct")
    flagged, chunk = wait_log(r"Flagged chat from BetaEmptyAct", since, timeout=40)
    report.check("empty actions still flags/cancels chat", flagged, chunk[-200:])
    if flagged and "executed 0 action(s)" in chunk:
        report.find(
            "medium",
            "Empty action list cancels chat but executes nothing (silent moderation)",
            "Players get no feedback beyond message vanishing",
        )
    rcon("neomod word remove betabreakword")
    rcon("neomod action reset")

    # Offline rule bypass attempts
    restore_baseline()
    rcon("neomod word add poison")
    bypass_cases = [
        ("POISON", True, "case sensitivity"),
        ("p o i s o n", True, "spaced letters"),
        ("p0ison", True, "leetspeak 0-for-o"),
        ("po1son", True, "leetspeak 1-for-i"),
        ("pоison", False, "cyrillic lookalike о"),  # cyrillic о U+043E
        ("poi​son", False, "zero-width joiner inside"),
        ("poison!", False, "punctuation suffix may fail whole-word"),
        ("xxpoisonxx", False, "embedded in larger token"),
    ]
    for message, expect_flag, label in bypass_cases:
        since = LOG.stat().st_size if LOG.exists() else 0
        user = "BetaBy" + str(abs(hash(label)) % 1000)
        try:
            chat(f"{MARKER} {message}", user)
        except Exception as exc:
            report.find("medium", f"Chat bot failed for bypass case {label}", str(exc))
            continue
        flagged, chunk = wait_log(rf"Flagged chat from {re.escape(user)}", since, timeout=25)
        if expect_flag and not flagged:
            report.find("high", f"Offline filter miss: {label}", f"message={message!r}")
        if not expect_flag and flagged:
            report.find("info", f"Offline filter caught optional case: {label}", f"message={message!r}")
        report.check(f"bypass probe ({label})", True, f"flagged={flagged} expect={expect_flag}")

    # Mute behavior: after flag with mute 3s, subsequent chat should be blocked
    restore_baseline()
    rcon("neomod action reset")
    rcon("neomod action remove clear")
    rcon("neomod action add mute 3s")
    rcon("neomod word add mutetest")
    since = LOG.stat().st_size if LOG.exists() else 0
    chat(f"{MARKER} mutetest", "BetaMute1")
    flagged, chunk = wait_log(r"Flagged chat from BetaMute1", since, timeout=40)
    report.check("mute action flags offender", flagged, chunk[-160:])
    # second message while muted should not appear as normal chat; may or may not log Flagged
    since2 = LOG.stat().st_size if LOG.exists() else 0
    chat(f"{MARKER} hello while muted", "BetaMute1")
    time.sleep(2)
    muted_chunk = log_since(since2)
    # If message is delivered to others, that's a bug. We can only see server log for chat format.
    if re.search(r"<BetaMute1> .*hello while muted", muted_chunk):
        report.find("critical", "Muted player chat still broadcast to server log as public chat", muted_chunk[-200:])
        report.check("muted player cannot publicly chat", False, muted_chunk[-200:])
    else:
        report.check("muted player cannot publicly chat", True, "no public chat line")

    # Mute does not persist across reload? (in-memory) — document as finding if so
    rcon("neomod reload")
    since3 = LOG.stat().st_size if LOG.exists() else 0
    chat(f"{MARKER} after reload should work unless persisted", "BetaMute1")
    time.sleep(2)
    after_reload = log_since(since3)
    if re.search(r"<BetaMute1>", after_reload) or "after reload" in after_reload:
        report.find("medium", "Mutes are memory-only and clear on /nmod reload", "Operators may expect mute to persist")
        report.check("mute persistence across reload", False, "mute cleared")
    else:
        # might still be muted if reload doesn't clear — also fine
        report.check("mute persistence across reload", True, "still muted or chat suppressed")

    # Concurrent command spam
    restore_baseline()
    errors = 0
    for i in range(30):
        try:
            rcon("neomod status")
            rcon("neomod action list")
            rcon(f"neomod word add spam{i}")
            rcon(f"neomod word remove spam{i}")
            rcon("neomod on")
            rcon("neomod off")
            rcon("neomod on")
        except Exception:
            errors += 1
    report.check("30-iteration command spam no RCON failures", errors == 0, f"errors={errors}")
    if errors:
        report.find("high", "RCON/command path fails under spam", f"errors={errors}")

    # Toggle race: off then immediate chat should pass
    rcon("neomod off")
    since = LOG.stat().st_size if LOG.exists() else 0
    msg = f"{MARKER}-disabled-{int(time.time())}"
    chat(msg, "BetaOffChat")
    ok, chunk = wait_log(re.escape(msg), since, timeout=30)
    report.check("moderation off lets chat through", ok, chunk[-160:])
    rcon("neomod on")

    # Category / config YAML: disable offline entirely via file
    text = CONFIG.read_text(encoding="utf-8")
    CONFIG.write_text(text.replace("  offline:\n    enabled: true", "  offline:\n    enabled: false"), encoding="utf-8")
    rcon("neomod reload")
    rcon("neomod word add shouldnotmatter")
    since = LOG.stat().st_size if LOG.exists() else 0
    chat(f"{MARKER} shouldnotmatter", "BetaOffOff")
    flagged, chunk = wait_log(r"Flagged chat from BetaOffOff", since, timeout=20)
    report.check("offline.enabled false disables word filter", not flagged, chunk[-120:])
    if flagged:
        report.find("high", "offline.enabled=false still flags banned words", chunk[-200:])

    # Invalid endpoint should fail-open
    restore_baseline()
    text = CONFIG.read_text(encoding="utf-8")
    text = re.sub(r'apiKey:\s*".*"', 'apiKey: "invalid-key"', text)
    text = re.sub(r'endpoint:\s*".*"', 'endpoint: "http://127.0.0.1:9/nope"', text)
    CONFIG.write_text(text, encoding="utf-8")
    rcon("neomod reload")
    since = LOG.stat().st_size if LOG.exists() else 0
    msg = f"{MARKER}-failopen-{int(time.time())}"
    chat(msg, "BetaFailOpen")
    ok, chunk = wait_log(re.escape(msg), since, timeout=30)
    report.check("bad cloud endpoint fail-open (chat delivered)", ok, chunk[-160:])
    if not ok:
        report.find("critical", "Fail-open broken: chat blocked when cloud unreachable", chunk[-200:])

    # Locale overwrite: customize locale file then reload — MessageService uses saveResource(..., true)
    locale = Path("/var/lib/pterodactyl/volumes/np-test-sandbox/plugins/NeoModeration/locale/en_US.yml")
    if locale.exists():
        original = locale.read_text(encoding="utf-8")
        locale.write_text(original.replace("NeoModeration", "CUSTOM_BRAND_TEST"), encoding="utf-8")
        rcon("neomod reload")
        status = strip(rcon("neomod status"))
        if "CUSTOM_BRAND_TEST" not in status and "NeoModeration" in status:
            report.find(
                "high",
                "Custom locale edits are wiped on every reload",
                "MessageService.saveResource(..., true) overwrites locale files",
            )
            report.check("custom locales survive reload", False, status[:80])
        else:
            report.check("custom locales survive reload", "CUSTOM_BRAND_TEST" in status, status[:80])

    # Status/action output readability
    restore_baseline()
    status = rcon("neomod status")
    if status.count("§") > 10:
        report.find("low", "Status output is heavy on legacy color codes", f"section-sign count={status.count('§')}")
    action_list = rcon("neomod action list")
    if "when chat is blocked" not in strip(action_list).lower():
        report.find("low", "Action list header missing or unclear", action_list[:120])

    # Permission: non-op player should not run admin commands — hard to test without perms API
    # Document as manual gap
    report.find("info", "No automated non-op permission denial test", "Needs a deopped player receiving command feedback")

    # Map art / usage commands — expected missing in 1.1.0
    map_help = strip(rcon("neomod help")).lower()
    if "map" not in map_help:
        report.find("high", "No map-art scanning commands or help entries", "Map items are not moderated in 1.1.0")
    usage_help = strip(rcon("neomod help")).lower()
    if "usage" not in usage_help and "credits" not in usage_help and "limit" not in usage_help:
        report.find("high", "No usage/credits/rate-limit commands for players or admins", "Cannot view Neomechanical quota in-game")

    # Final health
    plugins = rcon("plugins")
    report.check("plugin still loaded after abuse", "NeoModeration" in plugins, plugins[:120])
    version = rcon("version NeoModeration")
    report.check("version still 1.1.0", "1.1.0" in version, version[:80])

    restore_baseline()
    report.save()

    passed = sum(1 for c in report.checks if c["ok"])
    total = len(report.checks)
    print(f"\n=== BETA BREAK: {passed}/{total} checks, {len(report.findings)} findings ===", flush=True)
    for finding in report.findings:
        print(f" - [{finding.severity}] {finding.title}", flush=True)
    print(f"Wrote {OUT}", flush=True)
    # Exit 0 always — this is a bug hunt report, not a gate
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
