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
JAR = Path("/var/lib/pterodactyl/volumes/np-test-sandbox/plugins/NeoModeration-1.0.0.jar")
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
  if (/NeoModeration|\\/neomod config set/i.test(message)) result.sawHelp = true;
  if (/moderation\\.api\\.apiKey|Setup/i.test(message)) result.sawSetup = true;
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


def set_config(path: str, value: str) -> bool:
    response = rcon_command(f"neomod config set {path} {value}").lower()
    return path.lower() in response and ("updated" in response or "actualizado" in response)


def restore_config(enabled: str = "false", api_key: str = "", endpoint: str = "https://api.neomechanical.com/v1/events") -> None:
    set_config("locale", "en_US")
    set_config("moderation.enabled", enabled)
    set_config("moderation.offline.enabled", "true")
    set_config("moderation.offline.blockAnyUrl", "false")
    set_config("moderation.offline.normalizeLeetspeak", "true")
    if api_key:
        set_config("moderation.api.apiKey", api_key)
    else:
        rcon_command("neomod config clear moderation.api.apiKey")
    set_config("moderation.api.endpoint", endpoint)
    rcon_command("neomod reload")


def test_commands() -> tuple[bool, str]:
    cases = [
        ("neomod help", ("neomod", "config", "setup")),
        ("neomod status", ("enabled", "endpoint", "api key")),
        ("neomod config get moderation.enabled", ("moderation.enabled", "false")),
        ("neomod reload", ("reloaded",)),
    ]
    failures = []
    for command, expected in cases:
        response = rcon_command(command)
        if not all(part.lower() in response.lower() for part in expected):
            failures.append(f"{command}: {response.strip()}")
    return not failures, "; ".join(failures) if failures else "help/status/config/reload OK"


def test_config_set_get_restore() -> tuple[bool, str]:
    original = CONFIG.read_text(encoding="utf-8")
    endpoint_match = re.search(r'(?m)^(\s*endpoint:\s*)"([^"]*)"', original)
    original_endpoint = endpoint_match.group(2) if endpoint_match else "https://api.neomechanical.com/v1/events"
    set_ok = set_config("moderation.api.endpoint", "http://127.0.0.1:9/config-verify")
    get_ok = "http://127.0.0.1:9/config-verify" in rcon_command("neomod config get moderation.api.endpoint")
    key_set_ok = set_config("moderation.api.apiKey", "secret-test-key")
    key_mask_ok = "********" in rcon_command("neomod config get moderation.api.apiKey")
    restore_config("false", "", original_endpoint)
    return set_ok and get_ok and key_set_ok and key_mask_ok, f"set={set_ok}; get={get_ok}; keySet={key_set_ok}; keyMask={key_mask_ok}"


def test_rules_and_locale_commands() -> tuple[bool, str]:
    add_word = "Updated moderation.offline.bannedWords" in rcon_command("neomod rules add-word sandboxbad")
    list_word = "sandboxbad" in rcon_command("neomod rules list")
    remove_word = "Updated moderation.offline.bannedWords" in rcon_command("neomod rules remove-word sandboxbad")
    add_url = "Updated moderation.offline.bannedUrls" in rcon_command("neomod rules add-url sandbox.invalid")
    list_url = "sandbox.invalid" in rcon_command("neomod rules list")
    remove_url = "Updated moderation.offline.bannedUrls" in rcon_command("neomod rules remove-url sandbox.invalid")
    locale_set = set_config("locale", "es_ES")
    localized = "Estado de NeoModeration" in rcon_command("neomod status")
    set_config("locale", "en_US")
    rcon_command("neomod reload")
    ok = add_word and list_word and remove_word and add_url and list_url and remove_url and locale_set and localized
    return ok, (
        f"wordAdd={add_word}; wordList={list_word}; wordRemove={remove_word}; "
        f"urlAdd={add_url}; urlList={list_url}; urlRemove={remove_url}; locale={locale_set}; localized={localized}"
    )


def test_command_stress_loop() -> tuple[bool, str]:
    failures = []
    set_config("locale", "en_US")
    for index in range(12):
        word = f"stressword{index}"
        url = f"stress{index}.invalid"
        checks = {
            "help": "neomod" in rcon_command("neomod help").lower(),
            "status": "enabled" in rcon_command("neomod status").lower(),
            "get": "moderation.offline.enabled" in rcon_command("neomod config get moderation.offline.enabled"),
            "set": set_config("moderation.offline.normalizeLeetspeak", "true"),
            "addWord": "Updated moderation.offline.bannedWords" in rcon_command(f"neomod rules add-word {word}"),
            "listWord": word in rcon_command("neomod rules list"),
            "removeWord": "Updated moderation.offline.bannedWords" in rcon_command(f"neomod rules remove-word {word}"),
            "addUrl": "Updated moderation.offline.bannedUrls" in rcon_command(f"neomod rules add-url {url}"),
            "listUrl": url in rcon_command("neomod rules list"),
            "removeUrl": "Updated moderation.offline.bannedUrls" in rcon_command(f"neomod rules remove-url {url}"),
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
    restore_config("false", "", "https://api.neomechanical.com/v1/events")
    since = LOG.stat().st_size if LOG.exists() else 0
    message = f"{MARKER}-disabled-{int(time.time())}"
    send_player_chat(message, CLEAN_PLAYER)
    ok, chunk = wait_for(re.escape(message), timeout=30, since=since)
    return ok, chunk[-500:]


def test_offline_rules_without_api_key() -> tuple[bool, str]:
    rcon_command(f"deop {WORD_PLAYER}")
    restore_config("true", "", "https://api.neomechanical.com/v1/events")
    word = "sandboxwordlive"
    rcon_command(f"neomod rules remove-word {word}")
    rcon_command(f"neomod rules add-word {word}")
    list_ok = word in rcon_command("neomod rules list")
    rcon_command("neomod reload")
    since = LOG.stat().st_size if LOG.exists() else 0
    blocked_word = f"{MARKER}-offline-word-{int(time.time())}"
    send_player_chat(f"{blocked_word} {word}", WORD_PLAYER)
    word_ok, word_chunk = wait_for(rf"Flagged chat from NeoModWord via blocked_word:{re.escape(word)}", timeout=45, since=since)

    rcon_command(f"deop {URL_PLAYER}")
    set_config("moderation.offline.blockAnyUrl", "true")
    rcon_command("neomod reload")
    since = LOG.stat().st_size if LOG.exists() else 0
    blocked_url = f"{MARKER}-offline-url-{int(time.time())}"
    send_player_chat(f"{blocked_url} example.com", URL_PLAYER)
    url_ok, url_chunk = wait_for(r"Flagged chat from NeoModUrl via blocked_url:any", timeout=35, since=since)

    rcon_command(f"neomod rules remove-word {word}")
    restore_config("false", "", "https://api.neomechanical.com/v1/events")
    return list_ok and word_ok and url_ok, f"list={list_ok}; word={word_ok}; url={url_ok}; log={(url_chunk or word_chunk)[-400:]}"


def test_bad_endpoint_fail_open_and_breaker() -> tuple[bool, str]:
    rcon_command(f"deop {CHAT_PLAYER}")
    restore_config("true", "invalid-test-key", "http://127.0.0.1:9/unreachable-moderation")
    since = LOG.stat().st_size if LOG.exists() else 0
    prefix = f"{MARKER}-badapi-{int(time.time())}"
    for index in range(5):
        send_player_chat(f"{prefix}-{index}", f"NeoModApi{index}")
        time.sleep(0.8)
    chat_ok, chat_chunk = wait_for(re.escape(f"{prefix}-4"), timeout=35, since=since)
    breaker_ok, breaker_chunk = wait_for(r"Moderation paused|Moderation API rejected", timeout=40, since=since)
    restore_config("false", "", "https://api.neomechanical.com/v1/events")
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

    restore_config("false", "", "https://api.neomechanical.com/v1/events")
    ensure_bot()

    jar_ok = JAR.exists()
    report.add("NeoModeration-1.0.0.jar present", jar_ok, f"{JAR.stat().st_size if jar_ok else 0} bytes")
    plugins_output = rcon_command("plugins")
    version_output = rcon_command("version NeoModeration")
    report.add(
        "Plugin loaded v1.0.0",
        "NeoModeration" in plugins_output and "1.0.0" in version_output,
        version_output.strip()[:180],
    )

    ok, detail = test_commands()
    report.add("Core /neomod commands", ok, detail)
    ok, detail = test_config_set_get_restore()
    report.add("/neomod config set/get/mask/restore", ok, detail)
    ok, detail = test_rules_and_locale_commands()
    report.add("/neomod rules + locale commands", ok, detail)
    ok, detail = test_command_stress_loop()
    report.add("/neomod command stress loop", ok, detail)
    ok, detail = test_help_clickable_text_visible()
    report.add("/neomod help visible setup text", ok, detail)
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
