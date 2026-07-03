"""Send console commands to np-test-sandbox via RCON (localhost only)."""
from __future__ import annotations

import socket
import struct
import time

RCON_HOST = "127.0.0.1"
RCON_PORT = 25576
RCON_PASSWORD = "np-sandbox-local"


def _packet(req_id: int, req_type: int, payload: str) -> bytes:
    body = payload.encode("utf-8") + b"\x00\x00"
    return struct.pack("<iii", len(body) + 8, req_id, req_type) + body


def _read_packet(sock: socket.socket) -> tuple[int, int, str]:
    raw = b""
    while len(raw) < 4:
        chunk = sock.recv(4096)
        if not chunk:
            raise ConnectionError("RCON socket closed while reading packet size")
        raw += chunk
    size = struct.unpack("<i", raw[:4])[0]
    while len(raw) < size + 4:
        chunk = sock.recv(4096)
        if not chunk:
            raise ConnectionError("RCON socket closed while reading packet body")
        raw += chunk
    req_id, req_type = struct.unpack("<ii", raw[4:12])
    payload = raw[12 : 4 + size - 2].decode("utf-8", errors="replace")
    return req_id, req_type, payload


def rcon_command(command: str, host: str = RCON_HOST, port: int = RCON_PORT, password: str = RCON_PASSWORD) -> str:
    last_error: Exception | None = None
    for attempt in range(4):
        try:
            return _rcon_command_once(command, host, port, password)
        except (ConnectionError, ConnectionResetError, TimeoutError, OSError) as exc:
            last_error = exc
            time.sleep(0.35 * (attempt + 1))
    assert last_error is not None
    raise last_error


def _rcon_command_once(command: str, host: str, port: int, password: str) -> str:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(20)
        sock.connect((host, port))
        sock.sendall(_packet(1, 3, password))
        _read_packet(sock)
        sock.sendall(_packet(2, 2, command))
        _, _, response = _read_packet(sock)
        return response


def rcon_commands(*commands: str, delay: float = 0.45) -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(20)
        sock.connect((RCON_HOST, RCON_PORT))
        sock.sendall(_packet(1, 3, RCON_PASSWORD))
        _read_packet(sock)
        for cmd in commands:
            sock.sendall(_packet(2, 2, cmd))
            _read_packet(sock)
            if delay:
                time.sleep(delay)
