# NeoModeration Competitive Review and Product Roadmap

Research snapshot: 2026-07-11 (Asia/Ho_Chi_Minh)

## Executive conclusion

NeoModeration should become the **focused, privacy-conscious safety layer for
Minecraft servers**, not another all-in-one chat formatter.

The best market position is:

> Install in minutes. Run locally by default. Add semantic and image moderation
> when needed. See why every decision happened. Keep using the chat, permissions,
> and punishment plugins you already trust.

This uses NeoModeration's real advantages—offline resilience, semantic cloud
analysis, map-art image scanning, no-store requests, built-in actions, and measured
performance—while avoiding ChatControl's crowded channels/formatting/nicknames
market.

The immediate growth constraint is discovery and trust, not a lack of features.
The plugin was eight days old at this snapshot, had 11 Spigot downloads and no
ratings, and was not listed on Modrinth. The next product constraint is safe
adoption: admins need monitor-only rollout, test/explanation tools, exception
controls, staff visibility, and progressive actions before they will confidently
turn automated punishments on.

## NeoModeration data snapshot

### Adoption

| Signal | Value | Interpretation |
|---|---:|---|
| Spigot total downloads | 11 | Too early to infer retention or product-market fit |
| Spigot 1.0.0 downloads | 5 | Initial launch cohort |
| Spigot 1.2.0 downloads | 3 | Existing interest continued after first update |
| Spigot 1.2.2 downloads | 3 | Corrective release began receiving downloads immediately |
| Spigot ratings | 0 | No social proof yet |
| GitHub release downloads | 0 | Repository is private, so GitHub is not a public acquisition channel |
| GitHub views (14-day API window) | 7 / 4 unique | Mostly development/owner activity |
| GitHub clones (14-day API window) | 28 / 10 unique | Includes development/automation; not equivalent to users |

Sources: [Spiget resource API](https://api.spiget.org/v2/resources/136721),
[Spiget version API](https://api.spiget.org/v2/resources/136721/versions), GitHub
owner traffic APIs.

### Current product strengths

- Local word and URL rules work without an account or network call.
- Semantic cloud moderation supports configurable safety categories.
- Map-art image moderation is a clear differentiator in the reviewed market.
- Requests explicitly use `persistence: no_store` and disable learning.
- Built-in persistent mute, clear-chat, kick, and ban actions require no punishment
  dependency.
- Advanced configuration already supports command and role actions, although the
  simple in-game command surface does not expose them.
- Circuit breaking, timeouts, fail-open/fail-closed policy, and retry behavior are
  explicit.
- English and Spanish messages are bundled.
- The 403-word local filter benchmark is measured in single-digit microseconds per
  message.
- Paper 1.18.2, 1.19.4, 1.20.6, and 1.21.11 pass an automated server matrix with
  exactly-once moderation checks.

### Current trust and usability gaps

- No monitor-only/shadow mode for evaluating decisions before punishment.
- No `/nmod test <message>` explanation or rule preview.
- No allowlist/exceptions for safe phrases, domains, players, worlds, or channels.
- Cloud thresholds are fixed at 0.7 rather than configurable per category/preset.
- All detections share one global action list; severity/category-specific policy is
  not available through the simple UX.
- No local case history, strike history, staff alerts, or false-positive review
  workflow.
- No spam/caps/repetition/flood controls.
- Text coverage is chat-only; competitors cover commands, private messages, signs,
  books, anvils, and renamed items.
- Map art is scanned on hold/frame interaction, but there is no manual scan command,
  scan status, evidence view, or automatic frame-placement/chunk-load coverage.
- Powerful config-only actions (`COMMAND`, role actions, timeout) are hidden from the
  README and in-game action commands.
- No Modrinth or Hangar distribution, public support hub, gallery of in-game flows,
  public privacy page, or public compatibility page.
- The private GitHub repository has no public-facing description/homepage; the
  release link cannot serve as public social proof.

## Competitor evidence

Metrics below are point-in-time marketplace counters, not active-install counts.

| Product | Marketplace signal | Strongest capabilities | Opportunity for NeoModeration |
|---|---:|---|---|
| AI Chat Moderation | 3,916 Modrinth downloads / 20 followers; 2,567 Spigot downloads / 9 ratings (4.1) | No user API key, many platforms and versions, multilingual providers, per-category thresholds/actions | Beat it on transparent retention, offline safety, explainability, setup diagnostics, and multimodal map art |
| ChatProtect | 893 Spigot downloads / 7 ratings (4.4) | AI positioning and very broad 1.8–26.1 compatibility | Stronger evidence/testing, offline-first operation, and map-art story |
| ChatSentry | 690 Spiget-tracked downloads / 54 ratings (4.5), premium | Mature spam/advertising controls; chat, commands, signs, anvils, books | Add broad safety surfaces and spam without becoming a formatter |
| ChatFilter | 2,206 Modrinth downloads / 6 followers | Regex, allowlists, spam/caps/repetition, staff alerts, multiple text surfaces, four languages | Match practical controls, then differentiate with semantic/image moderation |
| Chat Guardian | 771 Modrinth downloads / 2 followers | Local embedding-based semantic similarity with no cloud dependency | Emphasize smaller footprint, policy categories, operational reliability, and map art |
| AutoMod | 229 Modrinth downloads / 1 follower | Perspective API, Redis networks, caching, spam, command-spam, LiteBans | Add integrations and network mode only after single-server UX is loved |
| PCM / Chat Moderator | 61 Modrinth downloads | Censor/block modes, score + decay, SQLite, staff alerts, Discord, social spy | Progressive enforcement and evidence are high-value gaps |
| ChatControl Red | 376 updates / 154 reviews on BuiltByBit | Extremely broad formatting, channels, anti-spam, networks, Discord, GUI, developer API | Do not chase its breadth; win on focus, clarity, privacy, and ten-minute setup |

Sources:

- [AI Chat Moderation](https://modrinth.com/plugin/ai-chat-moderation)
- [ChatProtect](https://www.spigotmc.org/resources/chatprotect-ai-powered-chat-moderation-1-8-26-1.117719/)
- [ChatSentry feature summary](https://wiki.chatsentry.xyz/feature-summary)
- [ChatFilter](https://modrinth.com/plugin/chatfilter-zepsizola)
- [Chat Guardian](https://modrinth.com/plugin/chat-guardian)
- [AutoMod](https://modrinth.com/plugin/automod)
- [PCM / Chat Moderator](https://modrinth.com/plugin/premiumchatmoderator)
- [ChatControl Red](https://builtbybit.com/resources/chatcontrol-format-filter-chat.18217/)
- Marketplace counters: Modrinth v2 project API and Spiget v2 resource API.

## What users reward—and resent

The strongest positive themes in competitor listings and reviews are:

1. It works immediately and does not lag the server.
2. It is difficult for players to bypass.
3. Admins can customize policy without writing Java.
4. Support is fast and the documentation is understandable.
5. It works across their existing network and moderation stack.
6. Staff can see what happened and take over when automation is uncertain.

The strongest negative themes are:

1. Confusing, thousand-line configuration and poorly worded docs.
2. Price or external-service cost that feels disproportionate to a small server.
3. False positives with no preview, exception, or appeal path.
4. Taking control away from the server owner.
5. Unclear privacy/retention when chat is sent to a third-party service.
6. A filter that only handles public chat while abuse moves to commands, private
   messages, books, signs, and renamed items.

This supports a focused product: broad safety coverage and strong owner control,
without unrelated chat-formatting bloat.

## Recommended product strategy

### Product promise

**“Safe chat and content in ten minutes, with local protection by default and
explainable cloud moderation when you want it.”**

### Design principles

- Safe to try: no punishment is required during evaluation.
- Owner-controlled: every category, threshold, exception, and action is explicit.
- Evidence before punishment: staff can understand and review important decisions.
- Resilient: local protection remains when cloud services fail.
- Composable: work with LiteBans, AdvancedBan, Essentials, DiscordSRV, and existing
  formatters rather than replacing them.
- Privacy legible: show exactly what leaves the server and how it is retained.
- Focused: do not add channels, nicknames, colors, prefixes, or general chat
  formatting.

## Prioritized roadmap

### P0 — Adoption and trust (highest impact, comparatively low effort)

1. **Monitor-only mode**
   - Log and alert on detections without blocking or punishing.
   - Provide a seven-day “what would have happened” summary.
   - Make monitor mode the recommended first cloud rollout.

2. **`/nmod test <message>` and `/nmod doctor`**
   - Test local and cloud decisions without executing actions.
   - Show matched rule/category, score/threshold when available, decision latency,
     circuit state, key scopes, and remaining quota.
   - Add clickable suggestions for the next setup step.

3. **Privacy and trust surface**
   - A one-screen `/nmod privacy` explanation: local vs cloud data, `no_store`, no
     learning, endpoint, timeout, and fail policy.
   - Publish a matching public privacy/data-flow page.
   - Display “Local only” or “Local + cloud” prominently in status.

4. **Marketplace conversion package**
   - Publish on Modrinth and Hangar with the same identity and version.
   - Add screenshots/GIFs for setup, status, a blocked message, map-art handling,
     monitor mode, and usage.
   - Add an honest 60-second installation video.
   - Publish a public docs/support site even if source remains private.
   - Add a support link, privacy link, compatibility table, changelog, and clear
     free-tier/quota explanation.

5. **Policy presets**
   - “Family Friendly,” “Community,” and “Minimal/Severe Only.”
   - Presets configure categories, thresholds, monitor mode, and actions while still
     allowing advanced overrides.

### P1 — Daily usefulness and moderator confidence

6. **Exceptions and false-positive control**
   - Allowed words/phrases/domains and word-boundary modes.
   - Per-world, per-channel, permission, and command exceptions.
   - `/nmod allow last` from a staff notification.
   - Dry-run impact preview before applying a rule change.

7. **Staff alerts and local case history**
   - Configurable staff permission, chat/action-bar alerts, and console-safe detail.
   - SQLite case log with player, content surface, reason, action, timestamp, and
     decision source; make raw content retention explicitly configurable.
   - `/nmod cases [player]` and `/nmod case <id>`.
   - Optional Discord webhook with redaction controls.

8. **Progressive enforcement**
   - Strikes by category/severity with decay.
   - First offense warn/block, repeated offense mute, severe/repeated offense kick
     or ban.
   - Category-specific action profiles and cooldowns.
   - Expose existing command/role actions in the simple command UX.

9. **Practical anti-spam**
   - Message rate, duplicate/similar message, repeated token, caps, character flood,
     and command-spam controls.
   - Run locally before any cloud call to reduce cost and latency.
   - Player-name and safe-command exemptions.

10. **Broader content surfaces**
    - Private-message commands, configurable commands, signs, books, anvils, and
      renamed items.
    - Use one shared `ContentModerationRequest` pipeline so policy and evidence are
      consistent across surfaces.
    - Allow each surface to be monitor, block, replace, or punish.

11. **Censor/replace action**
    - Optional replacement of local profanity while retaining the safe remainder of
      the message.
    - Keep semantic/high-severity cloud violations block-only by default.

### P2 — Differentiation and larger servers

12. **Map-art leadership**
    - Scan on item-frame placement and optionally on chunk discovery.
    - `/nmod scanmap` and map scan status.
    - Configurable warn/quarantine/confiscate behavior.
    - Staff preview with a blurred thumbnail and explicit permission.
    - Content hash cache so copied maps do not repeat cloud usage.

13. **Integrations**
    - First-class LiteBans/AdvancedBan/Essentials adapters while retaining generic
      console-command actions.
    - DiscordSRV/webhook integration.
    - PlaceholderAPI status/case placeholders.

14. **Folia and proxy/network support**
    - Folia-safe scheduling first.
    - Velocity/Bungee coordination only when real multi-server demand appears.
    - Redis or MySQL for shared strikes/cases as an optional module, not a base
      dependency.

15. **Quality feedback loop**
    - Staff can mark a case false positive/false negative.
    - Export a redacted evaluation set owned by the server.
    - Track precision by policy preset and language without silently retaining chat.

## What not to build now

- Chat channels, local/global chat, prefixes, nicknames, gradients, chat colors, or
  general formatting.
- A large inventory GUI before the command/test/doctor flows are excellent.
- Mandatory MySQL, Redis, ProtocolLib, or a punishment plugin.
- An embedded local LLM that materially increases jar size, memory, or startup time.
- Opaque telemetry. Any product analytics should be minimal, documented, and
  opt-in—or limited to aggregate Neomechanical API usage already visible to owners.

## Sequencing recommendation

### Next release: 1.3.0 “Trust and Control”

- Monitor-only mode.
- `/nmod test` and `/nmod doctor`.
- Policy presets and configurable category thresholds.
- Staff alerts.
- Allowed phrases/domains.
- Public docs/privacy page and Modrinth/Hangar launch.

This is the best first package because it improves activation, reduces fear of
false positives, creates screenshots worth marketing, and supplies the feedback
needed to design escalation accurately.

### Following release: 1.4.0 “Coverage”

- Local anti-spam.
- Signs/books/anvils/renamed items/configurable commands.
- Local case history and category-specific action profiles.
- Censor action.

### Later: 1.5.0 “Networks and Map Safety”

- Map-art placement/discovery/hash cache/manual review.
- Integrations, Folia, and optional proxy/shared-state support.

## Measurement plan

Do not evaluate success from download count alone.

| Funnel stage | Suggested metric |
|---|---|
| Discovery | Listing impressions/search rank by marketplace |
| Conversion | Listing view → jar download |
| Activation | First startup → healthy `/nmod status` |
| Cloud activation | Install → successful setup/doctor result |
| Safety adoption | Monitor mode → enforcement enabled |
| Trust | False-positive marks per 1,000 decisions; override rate |
| Quality | Precision/recall on maintained multilingual Minecraft test sets |
| Reliability | p50/p95 decision latency, timeout rate, circuit-open minutes |
| Cost | Cloud calls per active player/message and local-filter avoidance rate |
| Retention | Active server at day 7 and day 30 |
| Advocacy | Ratings, review rate, support resolution time, referrals |

The plugin should show server owners their own local metrics first. Product-level
metrics must avoid raw chat collection and be documented before collection begins.

## Strategy options

1. **Focused safety layer (recommended).** Build the P0/P1 roadmap above. This is
   differentiated, composable, and achievable without recreating a decade-old chat
   suite.
2. **All-in-one chat platform.** Add formatting, channels, networks, GUI, and social
   features. The addressable market is larger, but competition, configuration
   complexity, support burden, and implementation scope are dramatically higher.
3. **AI/multimodal specialist.** Concentrate on cloud categories, images, and model
   quality. This is distinctive but leaves everyday spam/evidence/integration gaps
   and makes adoption depend more heavily on service trust and cost.

The focused safety layer can later add selected multimodal capabilities without
losing its simplicity.
