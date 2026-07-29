# NeoModeration

**Monitor-first chat moderation for Minecraft.** Runs configurable word/link matching and anti-spam locally with no account required; fresh installs only report what they would do until you explicitly enable enforcement. The bundled `badword` and `scam` entries are safe setup examples, not a complete profanity list. Add optional cloud moderation for context-aware checks and NSFW map-art scanning.

Safe to try: **new installs start in monitor mode**, so you can see what *would* be actioned before anything blocks or punishes a player. Turn on enforcement when you're happy with the decisions.

## What it does

- **Configurable local word & link rules** — the matching engine is ready immediately. The shipped safe examples let `/nmod test badword` verify setup; add the real terms and domains your community needs. Configured terms catch leet-speak and spaced-out variants (`b4dw0rd`, `b a d w o r d`). Blocking starts only after `/nmod mode enforce`.
- **Anti-spam** — message rate, duplicate/near-duplicate messages, excessive caps (player names exempt), character floods, and command spam.
- **Progressive strikes** — repeated offences escalate automatically, and strikes decay over time.
- **Censor or block** — replace matched words with `****` and keep the rest of the message, or block it outright.
- **More than chat** — optionally moderate signs, books, anvil renames, and commands like `/msg`.
- **Map-art scanning** — filled maps are checked for inappropriate images (optional cloud feature).
- **Case history** — every detection is logged locally; browse it with `/nmod cases`.

## Trust & control

- `/nmod test badword` — prove the bundled local rule works without executing an action.
- `/nmod test <message>` — preview exactly how any message would be handled.
- `/nmod doctor` — check local setup, account/usage access, credits, and the last known moderation-event result.
- `/nmod mode monitor|enforce` — evaluate safely, then switch on enforcement.
- `/nmod preset family|community|minimal` — apply a ready-made policy in one command.
- `/nmod allow` — add exceptions that always win, to fix false positives instantly.
- Staff alerts show who was flagged, on which surface, and what happened.

## Setup

1. Drop the jar in `plugins/` and restart.
2. Run `/nmod test badword`; expect **FLAGGED** and **monitor alert only**. A fresh install still blocks nothing.
3. Optional cloud setup: [sign up](https://neomechanical.com/signup?src=neomoderation), create a key, run `/nmod setup <key>`, then `/nmod doctor` and `/nmod test hello`.
4. If `/nmod doctor` reports zero credits, use the [billing recovery page](https://neomechanical.com/billing?src=neomoderation_credits) and verify with `/nmod test hello`.
5. Review every enabled path, then run `/nmod mode enforce` only when ready.

## Commands

`/nmod help` shows everything, grouped into Getting started, Rules & actions, Tools, and Admin. Aliases: `/neomod`, `/nmod`, `/neomoderation`.

## Privacy

Local rules never leave your server. With a cloud key, checked messages are sent over HTTPS with no-store retention and no training use — run `/nmod privacy` to see exactly what is and isn't sent.

## Compatibility

Supports Bukkit, Spigot, Paper, and Purpur on Minecraft 1.18.2 through the 1.21.x line. Verified on Paper 1.18.2, 1.19.4, 1.20.6, and 1.21.11.
