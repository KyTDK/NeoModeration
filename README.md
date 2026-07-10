# NeoModeration

Chat **and map-art** moderation for Minecraft. Blocks bad words and links locally. Optionally uses the Neomechanical cloud for smarter scanning, including NSFW detection on filled maps.

## Setup

1. Drop `NeoModeration-1.2.1.jar` into `plugins/` and restart.
2. Local rules work immediately.
3. For cloud moderation, create an API key at [platform.neomechanical.com](https://platform.neomechanical.com) (scopes: `events:write` + `usage:read`), then run:

```text
/nmod setup YOUR_KEY
```

4. Confirm it worked: `/nmod status` should show cloud moderation **active**. Check quota with `/nmod usage`.

## What happens on detect

By default: clear chat spam + mute for 5 minutes (built-in mute, no extra plugins).

Change it with:

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
| `/nmod setup <apiKey>` | Turn on cloud moderation |
| `/nmod on` / `/nmod off` | Enable or disable |
| `/nmod key <apiKey>` | Save a new key |
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

## Map-art scanning

With a cloud key, filled maps are scanned for NSFW imagery when a player holds one or
right-clicks an item frame. Results are cached per map, and flagged maps are removed by
default. Tune it under `moderation.mapArt` in `config.yml`:

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

## Notes

- Without a key, only local word/link rules run.
- With a key, chat is also sent to `https://api.neomechanical.com/v1/events`.
- Mute is built into NeoModeration (no Essentials required).
- If the cloud is down, chat keeps working (fail-open).
- Edit `plugins/NeoModeration/config.yml` for advanced options.
