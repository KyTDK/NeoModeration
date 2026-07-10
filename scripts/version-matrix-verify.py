#!/usr/bin/env python3
"""Run NeoModeration against representative Java-17+ Paper versions."""
from __future__ import annotations

import json
import os
import re
import shutil
import socket
import struct
import subprocess
import sys
import time
import urllib.request
from dataclasses import dataclass
from pathlib import Path

VERSIONS = [
    ("1.18.2", "ghcr.io/pterodactyl/yolks:java_17"),
    ("1.19.4", "ghcr.io/pterodactyl/yolks:java_17"),
    ("1.20.6", "ghcr.io/pterodactyl/yolks:java_21"),
    ("1.21.11", "ghcr.io/pterodactyl/yolks:java_21"),
]
BASE_DIR = Path("/tmp/neomod-version-matrix")
REPORT_PATH = BASE_DIR / "results.json"
def default_jar() -> Path:
    candidates = sorted(
        Path("target").glob("NeoModeration-*.jar"),
        key=lambda path: path.stat().st_mtime,
        reverse=True,
    )
    return candidates[0] if candidates else Path("target/NeoModeration.jar")


JAR = Path(sys.argv[1]) if len(sys.argv) > 1 else default_jar()
VERSION_MATCH = re.search(r"NeoModeration-(.+)\.jar$", JAR.name)
EXPECTED_VERSION = VERSION_MATCH.group(1) if VERSION_MATCH else None
BOT_DIR = BASE_DIR / "mineflayer"
RCON_PASSWORD = "neomod-version-local"
USER_AGENT = "NeoModerationVerifier/1.0 (https://github.com/KyTDK/NeoModeration)"


@dataclass
class Result:
    version: str
    ok: bool
    detail: str


def log(message: str) -> None:
    print(message, flush=True)


def paper_download_url(version: str) -> str:
    url = f"https://fill.papermc.io/v3/projects/paper/versions/{version}/builds"
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        builds = json.load(response)
    for build in builds:
        if build.get("channel") == "STABLE":
            download = build.get("downloads", {}).get("server:default", {}).get("url")
            if download:
                return download
    raise RuntimeError(f"No stable Paper build found for {version}")


def download_server(version: str, target: Path) -> None:
    if target.exists():
        return
    url = paper_download_url(version)
    log(f"Downloading Paper {version}: {url}")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response, target.open("wb") as output:
        shutil.copyfileobj(response, output)


def packet(req_id: int, req_type: int, payload: str) -> bytes:
    body = payload.encode("utf-8") + b"\x00\x00"
    return struct.pack("<iii", len(body) + 8, req_id, req_type) + body


def read_packet(sock: socket.socket) -> tuple[int, int, str]:
    raw = b""
    while len(raw) < 4:
        chunk = sock.recv(4096)
        if not chunk:
            raise ConnectionError("RCON socket closed")
        raw += chunk
    size = struct.unpack("<i", raw[:4])[0]
    while len(raw) < size + 4:
        chunk = sock.recv(4096)
        if not chunk:
            raise ConnectionError("RCON socket closed")
        raw += chunk
    req_id, req_type = struct.unpack("<ii", raw[4:12])
    payload = raw[12 : 4 + size - 2].decode("utf-8", errors="replace")
    return req_id, req_type, payload


def rcon(command: str, port: int) -> str:
    last_error: Exception | None = None
    for attempt in range(5):
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
                sock.settimeout(15)
                sock.connect(("127.0.0.1", port))
                sock.sendall(packet(1, 3, RCON_PASSWORD))
                read_packet(sock)
                sock.sendall(packet(2, 2, command))
                return read_packet(sock)[2]
        except (ConnectionError, OSError, TimeoutError) as exc:
            last_error = exc
            time.sleep(0.5 * (attempt + 1))
    assert last_error is not None
    raise last_error


def wait_rcon(port: int, timeout: float = 180.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            rcon("list", port)
            return True
        except Exception:
            time.sleep(1)
    return False


def ensure_bot() -> None:
    BOT_DIR.mkdir(parents=True, exist_ok=True)
    if not (BOT_DIR / "node_modules/mineflayer").exists():
        subprocess.run(["npm", "install", "mineflayer", "--prefix", str(BOT_DIR), "--no-save"], check=True, timeout=180)
    (BOT_DIR / "chat.mjs").write_text(
        """import mineflayer from 'mineflayer';
const port = Number(process.argv[2]);
const msg = process.argv[3];
const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: 'NeoMatrix' });
bot.once('spawn', () => setTimeout(() => {
  bot.chat(msg);
  setTimeout(() => bot.quit(), 2500);
}, 2000));
bot.on('end', () => process.exit(0));
bot.on('error', (e) => { console.error(e.message); process.exit(1); });
setTimeout(() => process.exit(2), 70000);
""",
        encoding="utf-8",
    )


def run_container(version: str, image: str, index: int) -> Result:
    name = f"neomod-matrix-{version.replace('.', '-')}"
    work = BASE_DIR / version
    server_port = 25600 + index
    rcon_port = 25650 + index
    server_jar = work / "server.jar"
    log_path = work / "logs/latest.log"

    subprocess.run(["docker", "rm", "-f", name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    work.mkdir(parents=True, exist_ok=True)
    (work / "plugins").mkdir(exist_ok=True)
    download_server(version, server_jar)
    for generated in ("world", "world_nether", "world_the_end", "logs", "plugins/NeoModeration"):
        shutil.rmtree(work / generated, ignore_errors=True)
    shutil.copy2(JAR, work / "plugins" / JAR.name)
    (work / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (work / "server.properties").write_text(
        "\n".join(
            [
                f"server-port={server_port}",
                "online-mode=false",
                "white-list=false",
                "spawn-protection=0",
                "level-type=flat",
                "generate-structures=false",
                "view-distance=3",
                "simulation-distance=3",
                "max-players=4",
                "enable-rcon=true",
                f"rcon.port={rcon_port}",
                f"rcon.password={RCON_PASSWORD}",
                "broadcast-rcon-to-ops=false",
                "motd=NeoModeration version matrix",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    if sys.platform.startswith("linux") and os.geteuid() == 0:
        subprocess.run(["chown", "-R", "999:987", str(work)], check=True)
    startup = "java -Xms768M -Xmx1536M -Dterminal.jline=false -Dterminal.ansi=true -jar server.jar nogui"
    subprocess.run(
        [
            "docker",
            "run",
            "-d",
            "--name",
            name,
            "--user",
            "999:987",
            "-m",
            "2g",
            "-v",
            f"{work}:/home/container",
            "-p",
            f"127.0.0.1:{server_port}:{server_port}/tcp",
            "-p",
            f"127.0.0.1:{rcon_port}:{rcon_port}/tcp",
            "-e",
            f"STARTUP={startup}",
            "-e",
            "SERVER_MEMORY=1536",
            "-e",
            "SERVER_IP=0.0.0.0",
            "-e",
            f"SERVER_PORT={server_port}",
            "-e",
            "SERVER_JARFILE=server.jar",
            image,
        ],
        check=True,
        stdout=subprocess.DEVNULL,
    )

    try:
        if not wait_rcon(rcon_port):
            tail = log_path.read_text(errors="replace")[-2000:] if log_path.exists() else "no log"
            return Result(version, False, "RCON not ready: " + tail)

        def plain(text: str) -> str:
            return re.sub(r"§.", "", text).lower()

        checks = {
            "plugin": "NeoModeration" in rcon("plugins", rcon_port),
            "version": EXPECTED_VERSION is None or EXPECTED_VERSION in rcon("version NeoModeration", rcon_port),
            "help": "setup" in plain(rcon("neomod help", rcon_port)) and "action" in plain(rcon("neomod help", rcon_port)),
            "status": "status" in plain(rcon("neomod status", rcon_port)),
            "rules": "matrixbad" in plain(
                rcon("neomod word add matrixbad", rcon_port)
                + rcon("neomod word list", rcon_port)
            ),
            "actions": "mute 5m" in plain(rcon("neomod action reset", rcon_port) + rcon("neomod action list", rcon_port)),
            "kick": "kick" in plain(rcon("neomod action add kick", rcon_port)),
            "on": "on" in plain(rcon("neomod on", rcon_port)),
            "keyClear": "api key removed" in plain(rcon("neomod key clear", rcon_port)),
        }
        rcon("neomod reload", rcon_port)
        since = log_path.stat().st_size if log_path.exists() else 0
        subprocess.run(["node", str(BOT_DIR / "chat.mjs"), str(server_port), f"matrixbad {version}"], cwd=BOT_DIR, check=True, timeout=80)
        deadline = time.time() + 45
        moderation_log = "Flagged chat from NeoMatrix via blocked_word:matrixbad"
        moderation_count = 0
        while time.time() < deadline:
            chunk = log_path.read_bytes()[since:].decode("utf-8", errors="replace") if log_path.exists() else ""
            moderation_count = chunk.count(moderation_log)
            if moderation_count:
                break
            time.sleep(0.5)
        if moderation_count:
            time.sleep(2)
            chunk = log_path.read_bytes()[since:].decode("utf-8", errors="replace") if log_path.exists() else ""
            moderation_count = chunk.count(moderation_log)
        checks["offlineChatOnce"] = moderation_count == 1
        return Result(version, all(checks.values()), json.dumps(checks, sort_keys=True))
    finally:
        subprocess.run(["docker", "rm", "-f", name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def main() -> int:
    if not JAR.exists():
        print(f"Missing jar: {JAR}", file=sys.stderr)
        return 2
    ensure_bot()
    results = []
    for index, (version, image) in enumerate(VERSIONS):
        result = run_container(version, image, index)
        results.append(result)
        log(f"[{'PASS' if result.ok else 'FAIL'}] Paper {result.version}: {result.detail}")
        REPORT_PATH.write_text(
            json.dumps([result.__dict__ for result in results], indent=2),
            encoding="utf-8",
        )
    print("\n=== NEOMOD VERSION MATRIX ===")
    for result in results:
        print(f"[{'PASS' if result.ok else 'FAIL'}] Paper {result.version}: {result.detail}")
    print(f"Wrote {REPORT_PATH}")
    return 0 if all(result.ok for result in results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
