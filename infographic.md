# NeoModeration

**Chat safety for Minecraft** · Local rules · Optional cloud · One-command setup

## What it does

- Blocks banned words and links on your server (no account needed)
- Optional cloud scan for smarter moderation
- Mutes / clears chat when something is blocked
- Keeps chat working if the cloud is down

## Setup (60 seconds)

1. Put the jar in `plugins/` and restart
2. Local rules are already on
3. For cloud:

```text
/nmod setup YOUR_API_KEY
```

Get a key at https://platform.neomechanical.com → API keys → `events:write`

## Commands

```text
/nmod setup <apiKey>     turn on cloud moderation
/nmod on | off           enable / disable
/nmod key <apiKey>       save key
/nmod key clear          remove key
/nmod word add <word>    block a word
/nmod word remove <word>
/nmod word list
/nmod url add <link>     block a link
/nmod url remove <link>
/nmod url list
/nmod status
/nmod reload
```

## Permissions

| Permission | Who |
|------------|-----|
| `neomoderation.admin` | Ops (manage plugin) |
| `neomoderation.bypass` | Staff who skip checks |

## Default actions

When chat is blocked: clear the message, mute for 5 minutes.
Change actions in `plugins/NeoModeration/config.yml` if you need more.
