# NeoModeration

Chat moderation for Minecraft. Blocks bad words and links locally. Optionally uses the Neomechanical cloud for smarter scanning.

## Setup

1. Drop `NeoModeration-1.0.0.jar` into `plugins/` and restart.
2. Local rules work immediately.
3. For cloud moderation, create an API key at [platform.neomechanical.com](https://platform.neomechanical.com) (`events:write`), then run:

```text
/nmod setup YOUR_KEY
```

That’s it.

## Commands

| Command | What it does |
|---------|----------------|
| `/nmod setup <apiKey>` | Turn on cloud moderation |
| `/nmod on` / `/nmod off` | Enable or disable |
| `/nmod key <apiKey>` | Save a new key |
| `/nmod key clear` | Remove the key (local rules stay) |
| `/nmod word add\|remove\|list` | Manage blocked words |
| `/nmod url add\|remove\|list` | Manage blocked links |
| `/nmod status` | Quick status |
| `/nmod reload` | Reload config |

Aliases: `/neomod`, `/nmod`, `/neomoderation`.

## Permissions

- `neomoderation.admin` — use `/nmod` (ops by default)
- `neomoderation.bypass` — skip checks

## Notes

- Without a key, only local word/link rules run.
- With a key, chat is also sent to `https://api.neomechanical.com/v1/events`.
- If the cloud is down, chat keeps working (fail-open).
- Edit `plugins/NeoModeration/config.yml` for advanced options (actions, categories, timeouts).
