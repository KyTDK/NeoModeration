#!/usr/bin/env python3
"""Thorough NeoModeration verification against the live Minecraft container via RCON."""
from __future__ import annotations

import json
import re
import socket
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

CONTAINER = "575ac3f3-f74c-47a1-9a6d-33f1b78dd75d"
VOLUME = Path(f"/var/lib/pterodactyl/volumes/{CONTAINER}")
LOG = VOLUME / "logs/latest.log"
CONFIG = VOLUME / "plugins/NeoModeration/config.yml"
OUT = Path("/tmp/neomod-live-verify.json")
MARKER = "neomod-live"


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
        mark = "PASS" if ok else "FAIL"
        print(f"[{mark}] {name}" + (f" — {detail[:200]}" if detail else ""), flush=True)

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


def container_ip() -> str:
    out = subprocess.check_output(
        ["docker", "inspect", "-f", "{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}", CONTAINER],
        text=True,
    ).strip()
    if not out:
        raise RuntimeError("Could not resolve container IP")
    return out


def rcon_password() -> str:
    text = (VOLUME / "server.properties").read_text(encoding="utf-8")
    match = re.search(r"^rcon\.password=(.*)$", text, re.M)
    if not match:
        raise RuntimeError("rcon.password missing")
    return match.group(1).strip()


def rcon_port() -> int:
    text = (VOLUME / "server.properties").read_text(encoding="utf-8")
    match = re.search(r"^rcon\.port=(\d+)$", text, re.M)
    return int(match.group(1)) if match else 25575


def _packet(req_id: int, req_type: int, payload: str) -> bytes:
    body = payload.encode("utf-8") + b"\x00\x00"
    return struct.pack("<iii", len(body) + 8, req_id, req_type) + body


def _read_packet(sock: socket.socket) -> tuple[int, int, str]:
    raw = b""
    while len(raw) < 4:
        chunk = sock.recv(4096)
        if not chunk:
            raise ConnectionError("RCON closed")
        raw += chunk
    size = struct.unpack("<i", raw[:4])[0]
    while len(raw) < size + 4:
        chunk = sock.recv(4096)
        if not chunk:
            raise ConnectionError("RCON closed body")
        raw += chunk
    req_id, req_type = struct.unpack("<ii", raw[4:12])
    payload = raw[12 : 4 + size - 2].decode("utf-8", errors="replace")
    return req_id, req_type, payload


def rcon(command: str, host: str, port: int, password: str) -> str:
    last: Exception | None = None
    for attempt in range(5):
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
                sock.settimeout(20)
                sock.connect((host, port))
                sock.sendall(_packet(1, 3, password))
                _read_packet(sock)
                sock.sendall(_packet(2, 2, command))
                _, _, response = _read_packet(sock)
                return response
        except (OSError, ConnectionError, TimeoutError) as exc:
            last = exc
            time.sleep(0.4 * (attempt + 1))
    assert last is not None
    raise last


def wait_rcon(host: str, port: int, password: str, timeout: float = 120.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            rcon("list", host, port, password)
            return True
        except Exception:
            time.sleep(2)
    return False


def api_key() -> str:
    text = CONFIG.read_text(encoding="utf-8")
    match = re.search(r'(?m)^\s*apiKey:\s*"?([^"\n]*)"?\s*$', text)
    return (match.group(1) if match else "").strip()


def set_enabled(host: str, port: int, password: str, enabled: bool) -> str:
    return rcon("neomod on" if enabled else "neomod off", host, port, password)


def wait_log(pattern: str, since: int, timeout: float = 40.0) -> tuple[bool, str]:
    deadline = time.time() + timeout
    while time.time() < deadline:
        chunk = LOG.read_bytes()[since:].decode("utf-8", errors="replace") if LOG.exists() else ""
        if re.search(pattern, chunk, re.I):
            return True, chunk
        time.sleep(0.4)
    chunk = LOG.read_bytes()[since:].decode("utf-8", errors="replace") if LOG.exists() else ""
    return False, chunk


def strip_colors(text: str) -> str:
    return re.sub(r"§.", "", text).lower()


def post_events(key: str, text: str) -> tuple[int, str]:
    # Run from the Minecraft container network so Cloudflare accepts the request.
    payload = json.dumps(
        {
            "mode": "sync",
            "event": {
                "source": "minecraft",
                "adapter": "neomoderation",
                "eventType": "chat_message",
                "actor": {
                    "externalId": "00000000-0000-0000-0000-000000000099",
                    "username": "LiveVerify",
                    "displayName": "LiveVerify",
                },
                "context": {"scopeType": "minecraft_server", "tags": ["chat", "live-verify"]},
                "content": {"text": text, "attachments": []},
                "metadata": {
                    "platformPolicy": {
                        "thresholds": {
                            "sexual": 0.7,
                            "hate": 0.7,
                            "harassment": 0.7,
                            "violence": 0.7,
                            "scam": 0.7,
                            "spam": 0.7,
                            "illicit": 0.7,
                            "self-harm": 0.7,
                        }
                    },
                    "customInfo": {"plugin": "NeoModeration"},
                },
            },
            "options": {
                "persistence": "no_store",
                "includeAnalysisDetails": False,
                "learning": {"enabled": False, "mode": "off"},
            },
        }
    )
    subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "bash", "-lc", "cat > /tmp/nm-payload.json"],
        input=payload,
        text=True,
        check=True,
        timeout=10,
    )
    code = subprocess.check_output(
        [
            "docker",
            "exec",
            CONTAINER,
            "bash",
            "-lc",
            "curl -sS -o /tmp/nm-body.txt -w '%{http_code}' -X POST https://api.neomechanical.com/v1/events "
            f"-H 'Content-Type: application/json' -H 'Authorization: Bearer {key}' "
            "--data-binary @/tmp/nm-payload.json",
        ],
        text=True,
        timeout=30,
    ).strip()
    body = subprocess.check_output(
        ["docker", "exec", CONTAINER, "cat", "/tmp/nm-body.txt"],
        text=True,
        timeout=10,
    )
    return int(code), body


def main() -> int:
    report = Report()
    host = container_ip()
    port = rcon_port()
    password = rcon_password()

    ready = wait_rcon(host, port, password)
    report.add("Live RCON ready", ready, f"{host}:{port}")
    if not ready:
        report.save()
        return 2

    plugins = rcon("plugins", host, port, password)
    report.add("Plugin loaded", "NeoModeration" in plugins, plugins[:160])

    version = rcon("version NeoModeration", host, port, password)
    report.add("Plugin version 1.1.0", "1.1.0" in version, version[:160])

    help_text = rcon("neomod help", host, port, password)
    report.add(
        "Help shows simple setup",
        all(part in help_text.lower() for part in ("setup", "word", "url", "action", "on|off", "key")),
        help_text.replace("§", "")[:200],
    )
    report.add("Help has no YAML paths", "moderation.api" not in help_text and "config set" not in help_text.lower(), help_text[:120])

    off = strip_colors(rcon("neomod off", host, port, password))
    report.add("Off command", "off" in off, off)
    status_off = strip_colors(rcon("neomod status", host, port, password))
    report.add("Status OFF", "status: off" in status_off, status_off)

    on = strip_colors(rcon("neomod on", host, port, password))
    report.add("On command", "on" in on, on)
    status_on = strip_colors(rcon("neomod status", host, port, password))
    report.add("Status ON", "status: on" in status_on, status_on)

    key = api_key()
    report.add("API key present in config", bool(key) and key.startswith("nmt_"), f"len={len(key)}")

    # Re-apply key via new command path without printing it.
    key_save = strip_colors(rcon(f"neomod key {key}", host, port, password))
    report.add("Key save command", "api key saved" in key_save, key_save)
    status_cloud = strip_colors(rcon("neomod status", host, port, password))
    report.add("Status cloud yes", "cloud: yes" in status_cloud, status_cloud)

    word = f"livebad{int(time.time()) % 100000}"
    add_word = rcon(f"neomod word add {word}", host, port, password).lower()
    list_word = rcon("neomod word list", host, port, password)
    report.add("Word add/list", "add word" in add_word and word in list_word, f"{add_word} | {list_word[:120]}")

    url = f"livebad{int(time.time()) % 100000}.invalid"
    add_url = rcon(f"neomod url add {url}", host, port, password).lower()
    list_url = rcon("neomod url list", host, port, password)
    report.add("URL add/list", "add url" in add_url and url in list_url, f"{add_url} | {list_url[:120]}")

    remove_word = rcon(f"neomod word remove {word}", host, port, password).lower()
    remove_url = rcon(f"neomod url remove {url}", host, port, password).lower()
    report.add("Word/URL remove", "remove word" in remove_word and "remove url" in remove_url, f"{remove_word} | {remove_url}")

    reload = rcon("neomod reload", host, port, password).lower()
    report.add("Reload command", "reloaded" in reload, reload)

    reset_actions = rcon("neomod action reset", host, port, password).lower()
    report.add("Action reset", "reset" in reset_actions, reset_actions)
    action_list = rcon("neomod action list", host, port, password).lower()
    report.add("Action list default", "clear" in action_list and "mute 5m" in action_list, action_list)
    add_kick = rcon("neomod action add kick", host, port, password).lower()
    report.add("Action add kick", "kick" in add_kick, add_kick)
    add_mute = rcon("neomod action add mute 10m", host, port, password).lower()
    report.add("Action add mute 10m", "mute 10m" in add_mute, add_mute)
    status_actions = rcon("neomod status", host, port, password).lower()
    report.add("Status shows actions", "on detect" in status_actions and "kick" in status_actions, status_actions)
    remove_kick = rcon("neomod action remove kick", host, port, password).lower()
    report.add("Action remove kick", "removed action" in remove_kick, remove_kick)
    rcon("neomod action reset", host, port, password)

    # Cloud API from host using live key (same path plugin uses).
    status_code, body = post_events(key, "hello world clean message")
    report.add("Cloud API accepts clean text", status_code == 200, f"HTTP {status_code}: {body[:160]}")

    status_code, body = post_events(key, "im gonna rape you")
    blocked = status_code == 200 and '"status":"blocked"' in body
    report.add("Cloud API blocks severe text", blocked, f"HTTP {status_code}: {body[:200]}")

    # Bad key should 401, not 404 HTML.
    bad_code, bad_body = post_events("nmt_invalid_key_for_live_verify", "test")
    report.add("Cloud API rejects bad key with JSON 401", bad_code == 401 and "api key" in bad_body.lower(), f"HTTP {bad_code}: {bad_body[:160]}")

    # Endpoint health from container network namespace.
    try:
        probe = subprocess.check_output(
            [
                "docker",
                "exec",
                CONTAINER,
                "bash",
                "-lc",
                "curl -sS -o /tmp/nm-probe.txt -w '%{http_code}' -X POST https://api.neomechanical.com/v1/events "
                "-H 'Content-Type: application/json' -H 'Authorization: Bearer invalid' -d '{}'",
            ],
            text=True,
            timeout=30,
        ).strip()
        report.add("Container can reach api.neomechanical.com", probe == "401", f"HTTP {probe}")
    except Exception as exc:
        report.add("Container can reach api.neomechanical.com", False, str(exc))

    # Ensure setup command enables + sets key.
    setup = strip_colors(rcon(f"neomod setup {key}", host, port, password))
    report.add("Setup command", "cloud moderation is on" in setup, setup)
    status_final = strip_colors(rcon("neomod status", host, port, password))
    report.add(
        "Final status ready",
        "status: on" in status_final and "cloud: yes" in status_final,
        status_final,
    )

    # No old config path commands.
    old = rcon("neomod config set moderation.enabled true", host, port, password).lower()
    report.add("Old config path command rejected", "setup" in old or "usage" in old or "help" in old or "neomod" in old, old[:160])

    report.save()
    passed = sum(1 for c in report.cases if c.ok)
    total = len(report.cases)
    print(f"\n=== LIVE VERIFY: {passed}/{total} passed ===", flush=True)
    print(f"Wrote {OUT}", flush=True)
    return 0 if passed == total else 1


if __name__ == "__main__":
    raise SystemExit(main())
