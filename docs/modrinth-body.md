# NeoModeration

**Automatic chat moderation for Minecraft.** Blocks swearing, spam, advertising links, and inappropriate map art — instantly, with local rules that need no account or setup. Add optional cloud moderation when you want smarter, context-aware checks.

Safe to try: **new installs start in monitor mode**, so you can see what *would* be actioned before anything blocks or punishes a player. Turn on enforcement when you're happy with the decisions.

## What it does

- **Local word & link filtering** — works the instant you install it. Leet-speak and spaced-out bypasses are caught (`b4dw0rd`, `b a d w o r d`).
- **Anti-spam** — message rate, duplicate/near-duplicate messages, excessive caps (player names exempt), character floods, and command spam.
- **Progressive strikes** — repeated offences escalate automatically, and strikes decay over time.
- **Censor or block** — replace matched words with `****` and keep the rest of the message, or block it outright.
- **More than chat** — optionally moderate signs, books, anvil renames, and commands like `/msg`.
- **Map-art scanning** — filled maps are checked for inappropriate images (optional cloud feature).
- **Case history** — every detection is logged locally; browse it with `/nmod cases`.

## Trust & control

- `/nmod test <message>` — preview exactly how any message would be handled.
- `/nmod doctor` — one command checks your whole setup and connectivity.
- `/nmod mode monitor|enforce` — evaluate safely, then switch on enforcement.
- `/nmod preset family|community|minimal` — apply a ready-made policy in one command.
- `/nmod allow` — add exceptions that always win, to fix false positives instantly.
- Staff alerts show who was flagged, on which surface, and what happened.

## Setup

1. Drop the jar in `plugins/` and restart.
2. Local rules work immediately (in monitor mode on a fresh install).
3. Optional: run `/nmod setup <key>` for cloud moderation, then `/nmod doctor` to confirm.
4. Happy with it? `/nmod mode enforce`.

## Commands

`/nmod help` shows everything, grouped into Getting started, Rules & actions, Tools, and Admin. Aliases: `/neomod`, `/nmod`, `/neomoderation`.

## Privacy

Local rules never leave your server. With a cloud key, checked messages are sent over HTTPS with no-store retention and no training use — run `/nmod privacy` to see exactly what is and isn't sent.

## Compatibility

Works on Bukkit, Spigot, Paper, Purpur, and Folia-friendly forks, Minecraft 1.13 and newer. Verified on Paper 1.18.2, 1.19.4, 1.20.6, and 1.21.11.
