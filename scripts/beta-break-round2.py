#!/usr/bin/env python3
"""Round-2 adversarial beta tests for NeoModeration 1.1.0."""
from __future__ import annotations

import json
import re
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from np_test_rcon import rcon_command as rcon  # noqa: E402

LOG = Path("/var/lib/pterodactyl/volumes/np-test-sandbox/logs/latest.log")
CONFIG = Path("/var/lib/pterodactyl/volumes/np-test-sandbox/plugins/NeoModeration/config.yml")
OUT = Path("/tmp/neomod-beta-break-round2.json")
BOT_DIR = Path("/tmp/neomod-mineflayer")
CHAT_BOT = BOT_DIR / "neomod-chat-send.mjs"


@dataclass
class Finding:
    severity: str
    title: str
    detail: str


@dataclass
class Report:
    findings: list[Finding] = field(default_factory=list)
    checks: list[dict] = field(default_factory=list)

    def check(self, name: str, ok: bool, detail: str = "") -> None:
        self.checks.append({"name": name, "ok": ok, "detail": detail})
        print(f"[{'PASS' if ok else 'FAIL'}] {name}" + (f" — {detail[:240]}" if detail else ""), flush=True)

    def find(self, severity: str, title: str, detail: str) -> None:
        self.findings.append(Finding(severity, title, detail))
        print(f"[FIND:{severity.upper()}] {title} — {detail[:320]}", flush=True)

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


def ensure_bot() -> None:
    BOT_DIR.mkdir(parents=True, exist_ok=True)
    if not (BOT_DIR / "node_modules/mineflayer").exists():
        subprocess.run(["npm", "install", "mineflayer", "--prefix", str(BOT_DIR), "--no-save"], check=True, timeout=180)
    CHAT_BOT.write_text(
        """import mineflayer from 'mineflayer';
const msg = process.argv[2] || 'hello';
const username = process.argv[3] || 'BetaR2';
let sent = false;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25566, username });
bot.once('spawn', () => setTimeout(() => {
  bot.chat(msg);
  sent = true;
  setTimeout(() => bot.quit(), 2500);
}, 2000));
bot.on('end', () => process.exit(sent ? 0 : 3));
bot.on('error', (e) => { console.error(e.message); process.exit(1); });
setTimeout(() => process.exit(2), 60000);
""",
        encoding="utf-8",
    )


def chat(message: str, username: str, allow_disconnect: bool = False) -> int:
    """Send chat. Returns process exit code. Kick/ban may non-zero exit."""
    last = None
    for attempt in range(3):
        last = subprocess.run(
            ["node", str(CHAT_BOT), message, username],
            cwd=str(BOT_DIR),
            capture_output=True,
            text=True,
            timeout=70,
        )
        if last.returncode == 0 or allow_disconnect:
            return last.returncode
        time.sleep(2)
    raise RuntimeError(f"chat failed {username}: {(last.stderr or last.stdout)[-200:] if last else ''}")


def restore() -> None:
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


def wait_log(pattern: str, since: int, timeout: float = 35.0) -> tuple[bool, str]:
    deadline = time.time() + timeout
    while time.time() < deadline:
        chunk = LOG.read_bytes()[since:].decode("utf-8", errors="replace") if LOG.exists() else ""
        if re.search(pattern, chunk, re.I):
            return True, chunk
        time.sleep(0.35)
    chunk = LOG.read_bytes()[since:].decode("utf-8", errors="replace") if LOG.exists() else ""
    return False, chunk


def main() -> int:
    report = Report()
    ensure_bot()
    try:
        _run(report)
    finally:
        try:
            restore()
        except Exception:
            pass
        report.save()
        passed = sum(1 for c in report.checks if c["ok"])
        print(f"\n=== ROUND2: {passed}/{len(report.checks)} checks, {len(report.findings)} findings ===", flush=True)
        for f in report.findings:
            print(f" - [{f.severity}] {f.title}", flush=True)
        print(f"Wrote {OUT}", flush=True)
    return 0


def _run(report: Report) -> None:
    restore()

    # 1) Double-flag / double-action on single message
    rcon("neomod action reset")
    rcon("neomod word add doublehit")
    since = LOG.stat().st_size
    chat("say doublehit please", "DoubleHit1")
    ok, chunk = wait_log(r"Flagged chat from DoubleHit1", since, 40)
    count = len(re.findall(r"Flagged chat from DoubleHit1", chunk))
    report.check("single message produces one flag log", ok and count == 1, f"count={count}")
    if count > 1:
        report.find("critical", "Same chat message flagged multiple times (double listener?)", f"count={count}")
    rcon("neomod word remove doublehit")

    # 2) Ban action actually bans (cannot rejoin with same name in offline mode is weird)
    restore()
    rcon("neomod action reset")
    rcon("neomod action remove clear")
    rcon("neomod action remove mute")
    rcon("neomod action add ban")
    rcon("neomod word add banme")
    since = LOG.stat().st_size
    chat("please banme now", "BanTarget1", allow_disconnect=True)
    ok, chunk = wait_log(r"Flagged chat from BanTarget1", since, 40)
    report.check("ban action flags player", ok, chunk[-160:])
    # ban list
    banlist = strip(rcon("banlist"))
    report.check("player appears on banlist", "bantarget1" in banlist.lower(), banlist[:200])
    if ok and "bantarget1" not in banlist.lower():
        report.find("high", "BAN action did not add player to server ban list", banlist[:200])
    # cleanup ban
    rcon("pardon BanTarget1")
    rcon("neomod word remove banme")
    rcon("neomod action reset")

    # 3) Kick-only action
    rcon("neomod action reset")
    rcon("neomod action remove clear")
    rcon("neomod action remove mute")
    rcon("neomod action add kick")
    rcon("neomod word add kickme")
    since = LOG.stat().st_size
    chat("please kickme now", "KickTarget1", allow_disconnect=True)
    ok, chunk = wait_log(r"Flagged chat from KickTarget1", since, 40)
    kicked = "lost connection: Inappropriate chat message" in chunk or "KickTarget1 lost connection" in chunk
    report.check("kick action disconnects player", ok and kicked, chunk[-200:])
    if ok and not kicked:
        report.find("high", "KICK action did not disconnect player", chunk[-200:])
    rcon("neomod word remove kickme")
    rcon("neomod action reset")

    # 4) Action order: ban before clear — still should ban
    rcon("neomod action reset")
    # manually write order ban then clear
    text = CONFIG.read_text(encoding="utf-8")
    text = re.sub(
        r"  actions:.*?(?=  chat:)",
        "  actions:\n"
        "  - type: BAN\n"
        "    reason: \"order-ban\"\n"
        "  - type: CLEAR_CHAT\n",
        text,
        count=1,
        flags=re.S,
    )
    CONFIG.write_text(text, encoding="utf-8")
    rcon("neomod reload")
    rcon("neomod word add orderban")
    since = LOG.stat().st_size
    chat("orderban test", "OrderBan1", allow_disconnect=True)
    ok, chunk = wait_log(r"Flagged chat from OrderBan1", since, 40)
    banlist = strip(rcon("banlist")).lower()
    report.check("ban-first action order still bans", ok and "orderban1" in banlist, banlist[:160])
    rcon("pardon OrderBan1")
    rcon("neomod word remove orderban")

    # 5) URL bypass matrix
    restore()
    rcon("neomod url add evil.example")
    url_cases = [
        ("visit evil.example now", True, "plain domain"),
        ("https://evil.example/phish", True, "https url"),
        ("www.evil.example", True, "www prefix"),
        ("EVIL.EXAMPLE", True, "case"),
        ("evil[.]example", False, "bracket-dot obfuscation"),
        ("evil . example", False, "spaced dots"),
        ("evil[dot]example", False, "dot word obfuscation"),
        ("hxxps://evil.example", False, "hxxp scheme"),
        ("evil[.]example.com", False, "bracket in longer host"),
    ]
    for message, expect, label in url_cases:
        since = LOG.stat().st_size
        user = "Url" + str(abs(hash(label)) % 10000)
        try:
            chat(message, user)
        except Exception as exc:
            report.find("medium", f"chat failed for URL case {label}", str(exc))
            continue
        flagged, chunk = wait_log(rf"Flagged chat from {re.escape(user)}", since, 25)
        if expect and not flagged:
            report.find("high", f"URL filter miss: {label}", f"msg={message!r}")
        if not expect and flagged:
            report.find("info", f"URL filter caught optional obfuscation: {label}", f"msg={message!r}")
        report.check(f"url probe ({label})", True, f"flagged={flagged} expect={expect}")
    rcon("neomod url remove evil.example")

    # 6) blockAnyUrl mode
    restore()
    text = CONFIG.read_text(encoding="utf-8")
    CONFIG.write_text(text.replace("blockAnyUrl: false", "blockAnyUrl: true"), encoding="utf-8")
    rcon("neomod reload")
    since = LOG.stat().st_size
    chat("check https://example.com/page", "AnyUrl1")
    flagged, chunk = wait_log(r"Flagged chat from AnyUrl1 via blocked_url:any", since, 30)
    report.check("blockAnyUrl flags normal links", flagged, chunk[-160:])
    if not flagged:
        report.find("high", "blockAnyUrl=true did not flag https URL", chunk[-160:])

    # IP-like
    since = LOG.stat().st_size
    chat("join 1.2.3.4 now", "AnyUrlIp")
    flagged_ip, chunk = wait_log(r"Flagged chat from AnyUrlIp", since, 20)
    # IP may or may not match URL_PATTERN (needs tld-like). Document either way.
    if not flagged_ip:
        report.find("medium", "blockAnyUrl does not flag bare IPv4 addresses", "1.2.3.4 allowed")
    report.check("blockAnyUrl IPv4 probe completed", True, f"flagged={flagged_ip}")

    # 7) Blank / whitespace banned words in YAML
    restore()
    text = CONFIG.read_text(encoding="utf-8")
    text = text.replace(
        "    bannedWords:\n      - \"badword\"\n      - \"scam\"\n",
        "    bannedWords:\n      - \"badword\"\n      - \"\"\n      - \"   \"\n      - \"scam\"\n",
    )
    CONFIG.write_text(text, encoding="utf-8")
    try:
        reload_resp = strip(rcon("neomod reload"))
        report.check("reload with blank banned words", "reloaded" in reload_resp.lower(), reload_resp)
        since = LOG.stat().st_size
        chat("hello normal chat", "BlankWord1")
        time.sleep(2)
        chunk = LOG.read_bytes()[since:].decode("utf-8", errors="replace")
        false_flag = "Flagged chat from BlankWord1" in chunk
        report.check("blank banned words do not flag everything", not false_flag, chunk[-120:])
        if false_flag:
            report.find("critical", "Empty/whitespace banned word flags all chat", chunk[-200:])
    except Exception as exc:
        report.find("high", "Blank banned words break reload/runtime", str(exc))
        report.check("reload with blank banned words", False, str(exc))

    # 8) Corrupt actions YAML type
    restore()
    text = CONFIG.read_text(encoding="utf-8")
    text = re.sub(
        r"  actions:.*?(?=  chat:)",
        "  actions:\n  - type: NOT_A_REAL_ACTION\n  - type: MUTE\n    durationSeconds: 60\n    reason: \"x\"\n",
        text,
        count=1,
        flags=re.S,
    )
    CONFIG.write_text(text, encoding="utf-8")
    reload_resp = strip(rcon("neomod reload"))
    report.check("unknown action type does not crash reload", "reloaded" in reload_resp.lower(), reload_resp)
    listed = strip(rcon("neomod action list")).lower()
    # unknown becomes COMMAND with empty command — may show as "command"
    if "exception" in listed or "error" in listed:
        report.find("high", "Unknown action type surfaces errors in action list", listed[:160])
    report.check("action list after unknown type", True, listed[:160])

    # 9) Concurrent chats from many players with same banned word
    restore()
    rcon("neomod action reset")
    rcon("neomod action remove clear")  # reduce log spam
    rcon("neomod word add floodword")
    since = LOG.stat().st_size
    procs = []
    for i in range(8):
        procs.append(
            subprocess.Popen(
                ["node", str(CHAT_BOT), f"floodword from {i}", f"Flood{i}"],
                cwd=str(BOT_DIR),
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
        )
    for proc in procs:
        proc.wait(timeout=80)
    time.sleep(5)
    chunk = LOG.read_bytes()[since:].decode("utf-8", errors="replace")
    flags = len(re.findall(r"Flagged chat from Flood\d+", chunk))
    report.check("concurrent flood flags multiple players", flags >= 5, f"flags={flags}/8")
    if flags < 5:
        report.find("high", "Concurrent chat flood under-flags offenders", f"only {flags}/8 flagged")
    # server still alive
    report.check("server alive after flood", "NeoModeration" in rcon("plugins"), "")

    # 10) Rapid toggle during chat
    restore()
    rcon("neomod word add raceword")
    since = LOG.stat().st_size
    p = subprocess.Popen(
        ["node", str(CHAT_BOT), "raceword now", "RaceUser1"],
        cwd=str(BOT_DIR),
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    for _ in range(10):
        rcon("neomod off")
        rcon("neomod on")
    p.wait(timeout=80)
    time.sleep(2)
    chunk = LOG.read_bytes()[since:].decode("utf-8", errors="replace")
    report.check("rapid on/off during chat does not crash", "NeoModeration" in rcon("plugins"), chunk[-120:])
    # no assertion on flag — racey by design

    # 11) Category all disabled — cloud path only matters with key; offline still works
    restore()
    text = CONFIG.read_text(encoding="utf-8")
    for cat in ("sexual", "hate", "harassment", "violence", "scam", "spam", "illicit", "selfHarm"):
        text = text.replace(f"    {cat}: true", f"    {cat}: false")
    CONFIG.write_text(text, encoding="utf-8")
    rcon("neomod reload")
    rcon("neomod word add stillbad")
    since = LOG.stat().st_size
    chat("stillbad message", "CatOff1")
    flagged, chunk = wait_log(r"Flagged chat from CatOff1", since, 30)
    report.check("offline words still work when all categories disabled", flagged, chunk[-120:])
    if not flagged:
        report.find("medium", "Disabling all categories broke offline word filter", chunk[-120:])
    rcon("neomod word remove stillbad")

    # 12) scanAsyncChat false
    restore()
    text = CONFIG.read_text(encoding="utf-8")
    CONFIG.write_text(text.replace("scanAsyncChat: true", "scanAsyncChat: false"), encoding="utf-8")
    rcon("neomod reload")
    rcon("neomod word add noscan")
    since = LOG.stat().st_size
    chat("noscan should pass", "NoScan1")
    time.sleep(3)
    chunk = LOG.read_bytes()[since:].decode("utf-8", errors="replace")
    flagged = "Flagged chat from NoScan1" in chunk
    public = bool(re.search(r"<NoScan1>.*noscan", chunk))
    report.check("scanAsyncChat=false skips moderation", not flagged and public, f"flagged={flagged} public={public}")
    if flagged:
        report.find("high", "scanAsyncChat=false still moderated chat", chunk[-160:])
    rcon("neomod word remove noscan")

    # 13) Status/help injection via player name is N/A; command label injection
    weird = strip(rcon("neomod"))
    report.check("bare /neomod shows help", "setup" in weird.lower(), weird[:100])

    # 14) action add with extra garbage args
    restore()
    garbage = strip(rcon("neomod action add kick now please extra"))
    # should still add kick (extra args ignored) or reject
    listed = strip(rcon("neomod action list")).lower()
    if "kick" in listed:
        report.find("low", "action add ignores trailing garbage args instead of rejecting", garbage[:120])
    report.check("action add with trailing args handled", True, f"{garbage[:80]} | {listed[:80]}")

    # 15) CLEAR_CHAT alone is extremely noisy — measure broadcast lines
    restore()
    rcon("neomod action reset")
    rcon("neomod action remove mute")
    rcon("neomod word add noisyclear")
    since = LOG.stat().st_size
    chat("noisyclear", "Noise1")
    wait_log(r"Flagged chat from Noise1", since, 30)
    chunk = LOG.read_bytes()[since:].decode("utf-8", errors="replace")
    blank_lines = len(re.findall(r"\[Server thread/INFO\]:  \n", chunk)) + chunk.count("[Server thread/INFO]:  \r")
    # Paper logs blank broadcasts as lines with trailing space
    info_blanks = len([ln for ln in chunk.splitlines() if re.search(r"INFO\]:\s*$", ln) or re.search(r"INFO\]:\s+$", ln)])
    if info_blanks >= 50:
        report.find(
            "medium",
            "CLEAR_CHAT floods server log with ~90 blank broadcast lines per detection",
            f"blank-ish log lines≈{info_blanks}",
        )
    report.check("clear chat noise probe completed", True, f"blankish={info_blanks}")
    rcon("neomod word remove noisyclear")

    report.check("plugin healthy at end", "1.1.0" in rcon("version NeoModeration"), "")


if __name__ == "__main__":
    raise SystemExit(main())
