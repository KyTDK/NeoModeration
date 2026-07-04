# NeoModeration 1.1.0

**Chat safety for Minecraft** · Local rules · Optional cloud · One-command setup

## Setup (60 seconds)

1. Put the jar in `plugins/` and restart
2. Local rules are already on
3. For cloud:

```text
/nmod setup YOUR_API_KEY
```

Get a key at https://platform.neomechanical.com → API keys → `events:write`

## On detect

```text
/nmod action list
/nmod action add clear
/nmod action add mute 5m
/nmod action add kick
/nmod action add ban
/nmod action remove mute
/nmod action reset
```

Default: **clear** chat spam + **mute** 5 minutes (built-in).

## Commands

```text
/nmod setup <apiKey>
/nmod on | off
/nmod key <apiKey> | clear
/nmod action list | add | remove | reset
/nmod word add | remove | list
/nmod url add | remove | list
/nmod status
/nmod reload
```

## Permissions

| Permission | Who |
|------------|-----|
| `neomoderation.admin` | Ops (manage plugin) |
| `neomoderation.bypass` | Staff who skip checks |
