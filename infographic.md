# NeoModeration — Spigot / Bukkit Listing Copy

> **Current release:** 1.0.0
> **Purpose:** Minecraft adapter for the Neomechanical moderation platform.

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│  NEO MODERATION                                                              │
│  Minecraft chat safety · Offline rules · Neomechanical API · Actions        │
└──────────────────────────────────────────────────────────────────────────────┘
```

## Description

NeoModeration protects Minecraft chat with local offline rules and, optionally, the Neomechanical trust-and-safety platform. Without an API key it blocks configured words and URLs locally. With an API key it can also send player chat to the platform event API, receive a moderation decision, and apply configured Minecraft-side actions when content is blocked.

The plugin is intentionally separate from NeoPerformance. Performance tooling and trust-and-safety have different release cadence, support expectations, API-key handling, and privacy boundaries.

## Features

- Offline banned-word and banned-URL moderation with no API key required.
- Optional async chat moderation through `POST /v1/events`.
- Safe fail-open behavior: platform outages do not freeze chat.
- Circuit breaker after repeated transport, timeout, rate-limit, or server errors.
- Secure in-game setup with masked API-key reads.
- Configurable moderation categories and thresholds through generic platform policy hints.
- Configurable local rules through `/neomod rules`.
- Bundled locale files: `en_US`, `es_ES`.
- Stackable actions in order: `CLEAR_CHAT`, `MUTE`, `KICK`, `BAN`, `TIMEOUT`, `GIVE_ROLE`, `TAKE_ROLE`, `TEMP_ROLE`, `COMMAND`.
- Placeholders for command actions: `%PLAYER%`, `%UUID%`, `%ROLE%`, `%DURATION%`, `%REASON%`.
- Bypass permission: `neomoderation.bypass`.
- Admin command: `/neomod`.

## Setup

1. Install `NeoModeration-1.0.0.jar`.
2. Start the server once.
3. Run `/neomod config set moderation.enabled true`.
4. Optional: create a Neomechanical platform API key with `events:write`.
5. Optional: run `/neomod config set moderation.api.apiKey YOUR_KEY`.
6. Run `/neomod reload`.

Keep the key private. `/neomod config get moderation.api.apiKey` masks saved values.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/neomod help` | `neomoderation.admin` | Setup and command help |
| `/neomod status` | `neomoderation.admin` | Enabled/key/endpoint/circuit status |
| `/neomod reload` | `neomoderation.admin` | Reload config and reset circuit state |
| `/neomod config get <path>` | `neomoderation.admin` | Read safe scalar config |
| `/neomod config set <path> <value>` | `neomoderation.admin` | Update safe scalar config |
| `/neomod config clear moderation.api.apiKey` | `neomoderation.admin` | Remove the saved API key |
| `/neomod rules list` | `neomoderation.admin` | Show offline rules |
| `/neomod rules add-word <word>` | `neomoderation.admin` | Add a blocked word |
| `/neomod rules add-url <domain>` | `neomoderation.admin` | Add a blocked URL/domain fragment |

## Platform Boundary

NeoModeration owns Minecraft integration:

- deciding when to scan chat
- collecting player/message context
- sending generic platform event payloads
- applying returned decisions to the Minecraft server
- running local word and URL rules when the API key is absent or unnecessary

The Neomechanical platform owns:

- text and media analysis
- OCR and evidence extraction for image attachments
- scoring and verdicts
- incidents and review ledger
- usage metering and API keys

Map-art scanning is not advertised as production-ready in this release. Bukkit/Paper does not expose a stable cross-version API to extract arbitrary rendered map pixels from existing map items. When a tested extraction path exists, NeoModeration can send image attachments and let the platform run OCR through the standard media pipeline.

## Verification

- Unit tests cover config parsing, offline matching, locale parity, payload generation, response parsing, action hydration, circuit breaker behavior, and production hygiene.
- Sandbox verifier covers `/neomod` commands, config set/get/masking, rules commands, locale switching, disabled-chat delivery, no-key offline moderation, bad-endpoint fail-open behavior, circuit breaker logging, and live endpoint reachability.
