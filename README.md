# NeoModeration

NeoModeration is the Minecraft adapter for the Neomechanical moderation platform. It can scan Minecraft chat through `POST /v1/events`, apply returned block decisions, and execute server-side actions such as mute, timeout, kick, ban, role changes, clear-chat, or custom console commands. It also works fully offline with configurable banned words and URL rules when no API key is configured.

## Install

1. Put `NeoModeration-1.0.0.jar` in your server `plugins/` folder.
2. Start the server once so `plugins/NeoModeration/config.yml` is generated.
3. Run `/neomod config set moderation.enabled true`.
4. Optional: create a Neomechanical platform API key with `events:write`.
5. Optional: run `/neomod config set moderation.api.apiKey YOUR_KEY`.
6. Run `/neomod reload`.
7. When rotating or removing a key, run `/neomod config clear moderation.api.apiKey`.

The API key is stored only in `plugins/NeoModeration/config.yml`. `/neomod config get moderation.api.apiKey` masks saved values. If the key is empty, offline rules still run.

## Commands

- `/neomod help` shows setup help.
- `/neomod status` shows whether moderation is enabled, whether a key is configured, and the endpoint.
- `/neomod reload` reloads config and resets the circuit breaker.
- `/neomod config get <path>` reads safe config paths.
- `/neomod config set <path> <value>` updates safe scalar config paths.
- `/neomod config clear moderation.api.apiKey` removes the saved API key.
- `/neomod rules list` shows configured offline word and URL rules.
- `/neomod rules add-word <word>` / `remove-word <word>` manages offline word rules.
- `/neomod rules add-url <domain-or-fragment>` / `remove-url <domain-or-fragment>` manages offline URL rules.

## Offline Mode

Offline moderation is enabled by default under `moderation.offline`. It supports:

- whole-word banned-word checks with optional leetspeak normalization
- spaced-letter detection such as `s c a m`
- banned URL/domain fragments such as `grabify.link`
- optional `blockAnyUrl` mode for servers that want all links blocked
- the same action stack used by API moderation

Set `locale` to `en_US` or `es_ES` to change bundled command messages.

## Permissions

- `neomoderation.admin`: use `/neomod`.
- `neomoderation.bypass`: skip moderation checks.

## Platform Boundary

NeoModeration decides when to scan Minecraft events and how to enforce returned decisions. Offline word/URL rules run inside the plugin. The Neomechanical platform owns text/media analysis, OCR, scoring, incidents, and usage accounting when an API key is configured. Map-art/image moderation is not advertised as production-ready until there is a tested cross-version map pixel extraction path; the platform already supports OCR for image attachments when adapters can provide reliable image data.
