#!/usr/bin/env python3
"""
Full NeoModeration sandbox verification for np-test-sandbox.
Run on the Pterodactyl host after installing NeoModeration-1.0.0.jar.
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from np_test_rcon import rcon_command  # noqa: E402

CONTAINER = "np-test-sandbox"
LOG = Path("/var/lib/pterodactyl/volumes/np-test-sandbox/logs/latest.log")
CONFIG = Path("/var/lib/pterodactyl/volumes/np-test-sandbox/plugins/NeoModeration/config.yml")
PROPS = Path("/var/lib/pterodactyl/volumes/np-test-sandbox/server.properties")
JAR = Path("/var/lib/pterodactyl/volumes/np-test-sandbox/plugins/NeoModeration-1.1.0.jar")
OUT = Path("/tmp/neomod-sandbox-verify.json")
HELP_PLAYER = "FriedRizzler"
CHAT_PLAYER = "NeoModChat"
CLEAN_PLAYER = "NeoModClean"
WORD_PLAYER = "NeoModWord"
URL_PLAYER = "NeoModUrl"
MARKER = "neomod-verify"
BOT_DIR = Path("/tmp/neomod-mineflayer")
CHAT_BOT = BOT_DIR / "neomod-chat-send.mjs"
CONFIG_BOT = BOT_DIR / "neomod-config-test.mjs"


@dataclass
class Case:
    name: str
    ok: bool
    detail: str = ""


@dataclass
class Report:
    cases: list[Case] = field(default_factory=list)

    def add(self, name: str, ok: bool, detail: str = "") -> None:
        self.cases.append(Case(name, ok, detail))

    def save(self) -> None:
        OUT.write_text(
            json.dumps(
                {
                    "passed": sum(1 for c in self.cases if c.ok),
                    "total": len(self.cases),
                    "results": [{"name": c.name, "ok": c.ok, "detail": c.detail} for c in self.cases],
                },
                indent=2,
            ),
            encoding="utf-8",
        )


def log(message: str) -> None:
    print(message, flush=True)


def tail_since(since: int) -> str:
    if not LOG.exists():
        return ""
    return LOG.read_bytes()[since:].decode("utf-8", errors="replace")


def wait_for(pattern: str, timeout: float = 30.0, since: int = 0) -> tuple[bool, str]:
    deadline = time.time() + timeout
    while time.time() < deadline:
        chunk = tail_since(since)
        if re.search(pattern, chunk, re.I):
            return True, chunk
        time.sleep(0.5)
    return False, tail_since(since)


def wait_rcon_ready(timeout: float = 90.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            rcon_command("list")
            return True
        except Exception:
            time.sleep(1)
    return False


def ensure_offline_mode() -> bool:
    text = PROPS.read_text(encoding="utf-8")
    if re.search(r"^online-mode=false\s*$", text, re.M):
        return False
    PROPS.write_text(re.sub(r"^online-mode=.*$", "online-mode=false", text, flags=re.M), encoding="utf-8")
    return True


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
const msg = process.argv[2] || 'hello-bot';
const username = process.argv[3] || 'NeoModChat';
let sent = false;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25566, username });
bot.once('spawn', () => {
  setTimeout(() => {
    bot.chat(msg);
    sent = true;
    setTimeout(() => bot.quit(), 1000);
  }, 1000);
});
bot.on('end', () => process.exit(sent ? 0 : 3));
bot.on('error', (e) => { console.error('ERR', e.message); process.exit(1); });
setTimeout(() => process.exit(2), 60000);
""",
        encoding="utf-8",
    )
    CONFIG_BOT.write_text(
        """import mineflayer from 'mineflayer';
const result = { sawHelp: false, sawSetup: false, errors: [] };
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25566, username: 'FriedRizzler' });
bot.once('spawn', () => setTimeout(() => bot.chat('/neomod help'), 1500));
bot.on('messagestr', (message) => {
  if (/NeoModeration|\\/nmod setup|action list/i.test(message)) result.sawHelp = true;
  if (/setup <apiKey>|cloud moderation/i.test(message)) result.sawSetup = true;
});
setTimeout(() => {
  if (!result.sawHelp) result.errors.push('help did not appear');
  if (!result.sawSetup) result.errors.push('setup hint did not appear');
  console.log('RESULT_JSON=' + JSON.stringify(result));
  bot.quit();
  process.exit(result.errors.length ? 1 : 0);
}, 20000);
bot.on('error', (e) => { result.errors.push(e.message); console.log('RESULT_JSON=' + JSON.stringify(result)); process.exit(1); });
""",
        encoding="utf-8",
    )


def send_player_chat(message: str, username: str = CHAT_PLAYER) -> None:
    last: subprocess.CompletedProcess[str] | None = None
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
        time.sleep(5 + attempt * 2)
    assert last is not None
    raise RuntimeError(
        f"bot chat failed for {username}: exit={last.returncode}; stdout={last.stdout[-300:]}; stderr={last.stderr[-300:]}"
    )


def restore_config(
    enabled: bool = False,
    api_key: str = "",
    endpoint: str = "https://api.neomechanical.com/v1/events",
    block_any_url: bool = False,
    locale: str = "en_US",
) -> None:
    enabled_text = "true" if enabled else "false"
    block_any_url_text = "true" if block_any_url else "false"
    CONFIG.write_text(
        f"""locale: "{locale}"

moderation:
  enabled: {enabled_text}
  offline:
    enabled: true
    blockAnyUrl: {block_any_url_text}
    normalizeLeetspeak: true
    bannedWords:
      - "badword"
      - "scam"
    bannedUrls:
      - "grabify.link"
      - "discord.gg/free"
  api:
    endpoint: "{endpoint}"
    apiKey: "{api_key}"
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
    rcon_command("neomod reload")


def test_commands() -> tuple[bool, str]:
    cases = [
        ("neomod help", ("setup", "word", "url", "action")),
        ("neomod status", ("status", "cloud", "local rules", "on detect")),
        ("neomod off", ("off",)),
        ("neomod on", ("on",)),
        ("neomod reload", ("reloaded",)),
    ]
    failures = []
    for command, expected in cases:
        response = rcon_command(command)
        if not all(part.lower() in response.lower() for part in expected):
            failures.append(f"{command}: {response.strip()}")
    return not failures, "; ".join(failures) if failures else "help/status/on/off/reload OK"


def test_action_commands() -> tuple[bool, str]:
    reset_ok = "reset" in rcon_command("neomod action reset").lower()
    list_default = rcon_command("neomod action list").lower()
    default_ok = "clear" in list_default and "mute 5m" in list_default
    add_kick = "kick" in rcon_command("neomod action add kick").lower()
    add_ban = "ban" in rcon_command("neomod action add ban").lower()
    add_mute = "mute 10m" in rcon_command("neomod action add mute 10m").lower()
    listed = rcon_command("neomod action list").lower()
    list_ok = all(part in listed for part in ("clear", "mute 10m", "kick", "ban"))
    remove_ban = "removed action" in rcon_command("neomod action remove ban").lower()
    status = rcon_command("neomod status").lower()
    status_ok = "on detect" in status and "kick" in status and "ban" not in status.split("on detect", 1)[-1]
    rcon_command("neomod action reset")
    ok = reset_ok and default_ok and add_kick and add_ban and add_mute and list_ok and remove_ban and status_ok
    return ok, (
        f"reset={reset_ok}; default={default_ok}; kick={add_kick}; ban={add_ban}; "
        f"mute={add_mute}; list={list_ok}; remove={remove_ban}; status={status_ok}"
    )


def test_setup_and_key_commands() -> tuple[bool, str]:
    setup_ok = "cloud moderation is on" in rcon_command("neomod setup secret-test-key").lower()
    status_cloud = "cloud: yes" in rcon_command("neomod status").lower()
    key_clear_ok = "api key removed" in rcon_command("neomod key clear").lower()
    status_local = "local rules only" in rcon_command("neomod status").lower()
    key_set_ok = "api key saved" in rcon_command("neomod key secret-test-key").lower()
    restore_config(False, "", "https://api.neomechanical.com/v1/events")
    ok = setup_ok and status_cloud and key_clear_ok and status_local and key_set_ok
    return ok, (
        f"setup={setup_ok}; cloud={status_cloud}; clear={key_clear_ok}; "
        f"local={status_local}; key={key_set_ok}"
    )


def test_word_and_url_commands() -> tuple[bool, str]:
    add_word = "add word" in rcon_command("neomod word add sandboxbad").lower()
    list_word = "sandboxbad" in rcon_command("neomod word list")
    remove_word = "remove word" in rcon_command("neomod word remove sandboxbad").lower()
    add_url = "add url" in rcon_command("neomod url add sandbox.invalid").lower()
    list_url = "sandbox.invalid" in rcon_command("neomod url list")
    remove_url = "remove url" in rcon_command("neomod url remove sandbox.invalid").lower()
    restore_config(False, "", locale="es_ES")
    localized = "Estado:" in rcon_command("neomod status") or "Nube:" in rcon_command("neomod status")
    restore_config(False, "", locale="en_US")
    ok = add_word and list_word and remove_word and add_url and list_url and remove_url and localized
    return ok, (
        f"wordAdd={add_word}; wordList={list_word}; wordRemove={remove_word}; "
        f"urlAdd={add_url}; urlList={list_url}; urlRemove={remove_url}; localized={localized}"
    )


def test_command_stress_loop() -> tuple[bool, str]:
    failures = []
    for index in range(12):
        word = f"stressword{index}"
        url = f"stress{index}.invalid"
        checks = {
            "help": "setup" in rcon_command("neomod help").lower(),
            "status": "status" in rcon_command("neomod status").lower(),
            "on": "on" in rcon_command("neomod on").lower(),
            "addWord": "add word" in rcon_command(f"neomod word add {word}").lower(),
            "listWord": word in rcon_command("neomod word list"),
            "removeWord": "remove word" in rcon_command(f"neomod word remove {word}").lower(),
            "addUrl": "add url" in rcon_command(f"neomod url add {url}").lower(),
            "listUrl": url in rcon_command("neomod url list"),
            "removeUrl": "remove url" in rcon_command(f"neomod url remove {url}").lower(),
        }
        failed = [name for name, ok in checks.items() if not ok]
        if failed:
            failures.append(f"{index}:{','.join(failed)}")
    return not failures, "12 iterations OK" if not failures else "; ".join(failures)


def test_help_clickable_text_visible() -> tuple[bool, str]:
    rcon_command(f"op {HELP_PLAYER}")
    proc = subprocess.run(
        ["node", str(CONFIG_BOT)],
        cwd=str(BOT_DIR),
        capture_output=True,
        text=True,
        timeout=60,
    )
    rcon_command(f"deop {HELP_PLAYER}")
    output = proc.stdout + proc.stderr
    match = re.search(r"RESULT_JSON=(\{.*\})", output)
    if not match:
        return False, output[-600:]
    data = json.loads(match.group(1))
    return not data.get("errors"), json.dumps(data)


def test_disabled_chat_works() -> tuple[bool, str]:
    restore_config(False, "", "https://api.neomechanical.com/v1/events")
    since = LOG.stat().st_size if LOG.exists() else 0
    message = f"{MARKER}-disabled-{int(time.time())}"
    send_player_chat(message, CLEAN_PLAYER)
    ok, chunk = wait_for(re.escape(message), timeout=30, since=since)
    return ok, chunk[-500:]


def test_offline_rules_without_api_key() -> tuple[bool, str]:
    rcon_command(f"deop {WORD_PLAYER}")
    restore_config(True, "", "https://api.neomechanical.com/v1/events")
    word = "sandboxwordlive"
    rcon_command(f"neomod word remove {word}")
    rcon_command(f"neomod word add {word}")
    list_ok = word in rcon_command("neomod word list")
    rcon_command("neomod reload")
    since = LOG.stat().st_size if LOG.exists() else 0
    blocked_word = f"{MARKER}-offline-word-{int(time.time())}"
    send_player_chat(f"{blocked_word} {word}", WORD_PLAYER)
    word_ok, word_chunk = wait_for(rf"Flagged chat from NeoModWord via blocked_word:{re.escape(word)}", timeout=45, since=since)

    rcon_command(f"deop {URL_PLAYER}")
    restore_config(True, "", "https://api.neomechanical.com/v1/events", block_any_url=True)
    since = LOG.stat().st_size if LOG.exists() else 0
    blocked_url = f"{MARKER}-offline-url-{int(time.time())}"
    send_player_chat(f"{blocked_url} example.com", URL_PLAYER)
    url_ok, url_chunk = wait_for(r"Flagged chat from NeoModUrl via blocked_url:any", timeout=35, since=since)

    rcon_command(f"neomod word remove {word}")
    restore_config(False, "", "https://api.neomechanical.com/v1/events")
    return list_ok and word_ok and url_ok, f"list={list_ok}; word={word_ok}; url={url_ok}; log={(url_chunk or word_chunk)[-400:]}"


def test_bad_endpoint_fail_open_and_breaker() -> tuple[bool, str]:
    rcon_command(f"deop {CHAT_PLAYER}")
    restore_config(True, "invalid-test-key", "http://127.0.0.1:9/unreachable-moderation")
    since = LOG.stat().st_size if LOG.exists() else 0
    prefix = f"{MARKER}-badapi-{int(time.time())}"
    for index in range(5):
        send_player_chat(f"{prefix}-{index}", f"NeoModApi{index}")
        time.sleep(0.8)
    chat_ok, chat_chunk = wait_for(re.escape(f"{prefix}-4"), timeout=35, since=since)
    breaker_ok, breaker_chunk = wait_for(r"Cloud moderation paused|Cloud moderation rejected", timeout=40, since=since)
    restore_config(False, "", "https://api.neomechanical.com/v1/events")
    return chat_ok and breaker_ok, f"chat={chat_ok}; breaker={breaker_ok}; log={(breaker_chunk or chat_chunk)[-400:]}"


def probe_live_api() -> tuple[bool, str]:
    body = json.dumps(
        {
            "mode": "sync",
            "event": {
                "source": "minecraft",
                "adapter": "neomoderation",
                "eventType": "chat_message",
                "actor": {
                    "externalId": "00000000-0000-0000-0000-000000000001",
                    "username": "sandbox-test",
                    "displayName": "sandbox-test",
                },
                "context": {"scopeType": "minecraft_server", "tags": ["chat", "sandbox"]},
                "content": {"text": "test message for moderation probe", "attachments": []},
                "metadata": {"platformPolicy": {"thresholds": {"hate": 0.7, "sexual": 0.7}}},
            },
            "options": {"persistence": "no_store", "includeAnalysisDetails": False, "learning": {"enabled": False, "mode": "off"}},
        }
    ).encode()
    req = urllib.request.Request(
        "https://api.neomechanical.com/v1/events",
        data=body,
        method="POST",
        headers={"Content-Type": "application/json", "Authorization": "Bearer neomod-sandbox-probe-key"},
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            text = response.read(500).decode("utf-8", errors="replace")
            return True, f"HTTP {response.status}: {text[:200]}"
    except urllib.error.HTTPError as error:
        text = error.read(500).decode("utf-8", errors="replace")
        if error.code in (401, 403):
            return True, f"HTTP {error.code} (endpoint live, needs valid API key): {text[:200]}"
        return False, f"HTTP {error.code}: {text[:200]}"
    except Exception as exc:
        return False, str(exc)


def main() -> int:
    report = Report()
    running = subprocess.run(
        ["docker", "ps", "--filter", f"name={CONTAINER}", "--format", "{{.Names}}"],
        capture_output=True,
        text=True,
    ).stdout.strip()
    if CONTAINER not in running:
        report.add("sandbox container running", False, CONTAINER)
        report.save()
        return 2

    if ensure_offline_mode():
        log("Set online-mode=false; restarting sandbox once for bot login.")
        subprocess.run(["docker", "restart", CONTAINER], check=True)

    rcon_ready = wait_rcon_ready()
    report.add("RCON ready", rcon_ready, "localhost:25576")
    if not rcon_ready:
        report.save()
        return 2

    restore_config(False, "", "https://api.neomechanical.com/v1/events")
    ensure_bot()

    jar_ok = JAR.exists()
    report.add("NeoModeration-1.1.0.jar present", jar_ok, f"{JAR.stat().st_size if jar_ok else 0} bytes")
    plugins_output = rcon_command("plugins")
    version_output = rcon_command("version NeoModeration")
    report.add(
        "Plugin loaded v1.1.0",
        "NeoModeration" in plugins_output and "1.1.0" in version_output,
        version_output.strip()[:180],
    )

    ok, detail = test_commands()
    report.add("Core /nmod commands", ok, detail)
    ok, detail = test_action_commands()
    report.add("/nmod action commands", ok, detail)
    ok, detail = test_setup_and_key_commands()
    report.add("/nmod setup + key commands", ok, detail)
    ok, detail = test_word_and_url_commands()
    report.add("/nmod word + url commands", ok, detail)
    ok, detail = test_command_stress_loop()
    report.add("/nmod command stress loop", ok, detail)
    ok, detail = test_help_clickable_text_visible()
    report.add("/nmod help visible setup text", ok, detail)
    ok, detail = probe_live_api()
    report.add("Neomechanical /v1/events endpoint", ok, detail)
    ok, detail = test_disabled_chat_works()
    report.add("Disabled moderation lets chat through", ok, detail)
    ok, detail = test_offline_rules_without_api_key()
    report.add("Offline moderation without API key", ok, detail)
    ok, detail = test_bad_endpoint_fail_open_and_breaker()
    report.add("Bad endpoint fail-open + circuit breaker", ok, detail)

    report.save()
    passed = sum(1 for case in report.cases if case.ok)
    total = len(report.cases)
    log(f"\n=== NEOMOD SANDBOX VERIFY: {passed}/{total} passed ===")
    for case in report.cases:
        mark = "PASS" if case.ok else "FAIL"
        line = f"[{mark}] {case.name}"
        if case.detail:
            line += f" — {case.detail[:180]}"
        log(line)
    log(f"\nWrote {OUT}")
    return 0 if passed == total else 1


if __name__ == "__main__":
    raise SystemExit(main())
