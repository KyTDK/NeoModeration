# NeoModeration 1.4.1

**Monitor-first Minecraft moderation** · Local rules · Optional cloud · NSFW map-art scanning

## Setup (60 seconds)

1. Put the jar in `plugins/` and restart
2. Run `/nmod test badword` — expect **FLAGGED** + **monitor alert only**
3. Optional cloud setup:

```text
/nmod setup YOUR_API_KEY
/nmod doctor
```

Sign up at https://neomechanical.com/signup?src=neomoderation, create a key
with `events:write` + `usage:read`, and use `/nmod doctor` to verify credits.
If credits reach zero: https://neomechanical.com/billing?src=neomoderation_credits
4. Review every enabled path; nothing blocks or punishes until
   `/nmod mode enforce`

## On detect

Monitor mode alerts staff and records what would happen. Enforce mode blocks
flagged chat and runs the configured actions:

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
/nmod test <message>
/nmod doctor
/nmod mode monitor | enforce
/nmod on | off
/nmod key set <apiKey> | clear
/nmod action list | add | remove | reset
/nmod word add | remove | list
/nmod url add | remove | list
/nmod usage
/nmod status
/nmod reload
```

## Permissions

| Permission | Who |
|------------|-----|
| `neomoderation.admin` | Ops (manage plugin) |
| `neomoderation.bypass` | Staff who skip checks |
