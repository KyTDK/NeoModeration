# NeoModeration

Chat **and map-art** moderation for Minecraft. Matches configurable word/link rules and detects spam locally, then can block detections when you explicitly enable enforcement. The bundled `badword` and `scam` entries are safe setup examples, not a complete profanity list. Optional Neomechanical cloud checks add context-aware scanning, including NSFW detection on filled maps.

**Safe to try:** new installations start in **monitor mode** — detections are logged and alerted to staff, but nothing is blocked or punished until you turn enforcement on. See exactly what data stays on your server with `/nmod privacy` ([privacy details](docs/PRIVACY.md)).

## Setup

1. Drop `NeoModeration-1.4.1.jar` into `plugins/` and restart.
2. Run `/nmod test badword`. It should show the bundled local rule as **FLAGGED** and the result as **monitor alert only**. This is a dry run, and a fresh install does not block or punish anyone.
3. Optional: for context-aware cloud moderation, [sign up](https://neomechanical.com/signup?src=neomoderation), create an API key with `events:write` + `usage:read`, then save it:

```text
/nmod setup YOUR_KEY
```

4. Run `/nmod doctor` to verify the account/usage API, latency, and exact credit balance. If credits are exhausted, it links directly to the [billing recovery page](https://neomechanical.com/billing?src=neomoderation_credits). Then use `/nmod test hello` to verify the moderation-events path safely.
5. Review every enabled path, then turn blocking on only when you are ready:

```text
/nmod mode enforce
```

## Coverage

Beyond chat, all running on local rules (no API key needed):

- **Anti-spam** — message rate, duplicate/similar messages, caps, character floods, command-rate. On by default; tune under `moderation.spam` or disable per-check.
- **Censor** — set `moderation.chat.censorLocalDetections: true` to replace matched words with `****` instead of blocking the whole message.
- **Strikes** — detections accumulate and decay; reaching a rung runs an extra action (default kick at 4). Configure `moderation.strikes`.
- **More surfaces** — signs, books, anvil renames, and `/msg`-style commands. Each independently `off`/`monitor`/`censor`/`block` under `moderation.surfaces` (all off by default).
- **Case history** — `/nmod cases [player]` and `/nmod case <id>` browse a local SQLite log of detections.

## Trust & control

- **Monitor mode** (`/nmod mode monitor|enforce`) — evaluate decisions without any risk to players. `/nmod mode` shows what would have happened since startup.
- **Explain any decision** — `/nmod test badword` proves the bundled local rule works; `/nmod test <message>` dry-runs the full local + cloud pipeline.
- **Setup diagnostics** — `/nmod doctor` finds misconfigurations before they bite.
- **Exceptions** — `/nmod allow word|url add <value>` fixes false positives instantly; allowed phrases/links always win over banned rules.
- **Policy presets** — `/nmod preset family|community|minimal` bundles category thresholds + actions. Each cloud category also accepts a custom threshold (0.05–0.99) in `config.yml`.
- **Staff alerts** — players with `neomoderation.notify` see every detection in-game (message preview optional).
- **Privacy surface** — `/nmod privacy` shows what runs locally, what the cloud receives, and the `no_store`/no-training guarantees.

## What happens on detect

In **enforce** mode, by default: clear chat spam + mute for 5 minutes (built-in mute, no extra plugins). In **monitor** mode: staff alert + log only.

Change actions with:

```text
/nmod action list
/nmod action add clear
/nmod action add mute 10m
/nmod action add kick
/nmod action add ban
/nmod action remove mute
/nmod action reset
```

Mute durations: `30s`, `5m`, `1h`, `1d` (or bare seconds).

## Commands

| Command | What it does |
|---------|----------------|
| `/nmod setup <apiKey>` | Save a cloud key; verify it with `/nmod doctor` |
| `/nmod mode [monitor\|enforce]` | Show or switch enforcement mode |
| `/nmod test <message>` | Preview how a message would be moderated |
| `/nmod doctor` | Diagnose configuration, account API, and known event health |
| `/nmod cases [player]` / `/nmod case <id>` | Browse the local detection history |
| `/nmod preset <family\|community\|minimal>` | Apply a policy preset |
| `/nmod allow word\|url add\|remove\|list` | Manage exceptions (always win) |
| `/nmod privacy` | Show what data stays local vs. cloud |
| `/nmod on` / `/nmod off` | Enable or disable |
| `/nmod key set <apiKey>` | Save a new key; verify it with `/nmod doctor` |
| `/nmod key clear` | Remove the key (local rules stay) |
| `/nmod action list` | Show actions on detect |
| `/nmod action add <clear\|mute\|kick\|ban> [time]` | Add an action |
| `/nmod action remove <clear\|mute\|kick\|ban>` | Remove an action |
| `/nmod action reset` | Back to clear + mute 5m |
| `/nmod word add\|remove\|list` | Manage blocked words |
| `/nmod url add\|remove\|list` | Manage blocked links |
| `/nmod usage` | Show cloud credits, limits, and requests |
| `/nmod status` | Quick status |
| `/nmod reload` | Reload config |

Aliases: `/neomod`, `/nmod`, `/neomoderation`.

## Compatibility

Supports Bukkit, Spigot, Paper, and Purpur on Minecraft 1.18.2 through the
1.21.x line. The release matrix verifies Paper 1.18.2, 1.19.4, 1.20.6, and
1.21.11.

## Map-art scanning

With a cloud key, filled maps are scanned for NSFW imagery when a player holds one or
right-clicks an item frame. Results are cached per map, and flagged maps are removed by
default (in monitor mode, staff are alerted instead). Tune it under `moderation.mapArt`
in `config.yml`:

```yaml
moderation:
  mapArt:
    enabled: true
    scanOnHold: true
    scanOnFrameInteract: true
    confiscate: true      # false = warn only, keep the item
    cacheSize: 1000
```

## Permissions

- `neomoderation.admin` — use `/nmod` (ops by default)
- `neomoderation.bypass` — skip checks
- `neomoderation.notify` — receive in-game detection alerts (ops by default)

## Notes

- Without a key, only local word/link rules run — no chat or map content leaves your server. Content-free bStats technical metrics are documented in [docs/PRIVACY.md](docs/PRIVACY.md).
- With a key, checked chat is sent to `https://api.neomechanical.com/v1/events` with `no_store` retention and training disabled. Details: [docs/PRIVACY.md](docs/PRIVACY.md).
- Mute is built into NeoModeration (no Essentials required).
- If the cloud is down, chat keeps working (fail-open) and local rules still run.
- Servers upgrading from 1.2.x keep enforcing exactly as before; monitor mode is only the default for brand-new installs.
- Edit `plugins/NeoModeration/config.yml` for advanced options.

## Maintainers

The build, marketplace publishing, review, and post-release verification
process is documented in [docs/RELEASING.md](docs/RELEASING.md).
