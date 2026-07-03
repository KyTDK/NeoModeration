# NeoModeration

NeoModeration is the Minecraft adapter for the Neomechanical moderation platform. It scans Minecraft chat through `POST /v1/events`, applies returned block decisions, and executes server-side actions such as mute, timeout, kick, ban, role changes, clear-chat, or custom console commands.

## Install

1. Put `NeoModeration-1.0.0.jar` in your server `plugins/` folder.
2. Start the server once so `plugins/NeoModeration/config.yml` is generated.
3. Create a Neomechanical platform API key with `events:write`.
4. Run `/neomod config set moderation.api.apiKey YOUR_KEY`.
5. Run `/neomod config set moderation.enabled true`.
6. Run `/neomod reload`.
7. When rotating or removing a key, run `/neomod config clear moderation.api.apiKey`.

The API key is stored only in `plugins/NeoModeration/config.yml`. `/neomod config get moderation.api.apiKey` masks saved values.

## Commands

- `/neomod help` shows setup help.
- `/neomod status` shows whether moderation is enabled, whether a key is configured, and the endpoint.
- `/neomod reload` reloads config and resets the circuit breaker.
- `/neomod config get <path>` reads safe config paths.
- `/neomod config set <path> <value>` updates safe scalar config paths.
- `/neomod config clear moderation.api.apiKey` removes the saved API key.

## Permissions

- `neomoderation.admin`: use `/neomod`.
- `neomoderation.bypass`: skip moderation checks.

## Platform Boundary

NeoModeration decides when to scan Minecraft events and how to enforce returned decisions. The Neomechanical platform owns text/media analysis, OCR, scoring, incidents, and usage accounting. Map-art/image moderation is not advertised as production-ready until there is a tested cross-version map pixel extraction path; the platform already supports OCR for image attachments when adapters can provide reliable image data.
