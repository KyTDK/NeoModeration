# Release Automation Cleanup Design

## Goal

Turn the scripts created while publishing NeoModeration 1.4.0 into a small,
repeatable release kit. The result must preserve every proven publishing path,
remove one-off Hangar probes, and document the manual review steps that cannot
be automated safely.

## Current state

The repository already contains reusable publishers for SpigotMC and Modrinth.
The 1.4.0 Hangar session produced a reusable CDP helper and a generalized
`hangar-upload-version.mjs` flow, plus several version-specific diagnostic
scripts. The diagnostic scripts contain hard-coded 1.4.0 values and temporary
screenshot paths and are not suitable for later releases.

The live audit on July 13, 2026 found:

- GitHub Releases, SpigotMC, and Hangar are public.
- Hangar version 1.4.0 is reviewed and public.
- Modrinth's public project endpoint returns 404 and public search returns no
  NeoModeration project. The existing publisher deliberately creates the
  project as a draft, so the release procedure must include submission and
  public-verification steps instead of assuming publication succeeded.
- bStats is receiving data from one server, but the three custom charts are not
  present in the public dashboard metadata and therefore cannot yet be audited
  from the public page.

## Selected approach

Use a documented, channel-by-channel release procedure backed by focused
publisher scripts. Do not add an all-in-one publisher: each marketplace has a
different authentication and review model, and an orchestrator could fail
halfway through while making it unclear which external mutations completed.

### Files to retain

- `scripts/spigot-publish.mjs`: logged-in Chrome automation for SpigotMC.
- `scripts/modrinth-publish.mjs`: Modrinth project/version creation through its
  API, with logged-in Chrome used only to establish publishing credentials.
- `scripts/cdp-lib.mjs`: minimal CDP helper used by Hangar automation.
- `scripts/hangar-upload-version.mjs`: proven Hangar version wizard using the
  public GitHub release JAR as its external download.
- `scripts/hangar-publish.mjs`: optional token-based Hangar API path. It will be
  documented as an alternative, not the proven default.
- `scripts/hangar-entercode.mjs`: account verification helper, documented as a
  setup-only utility.
- `media/icon-spigot.png`: the published SpigotMC icon asset.

### Files to remove

Remove these one-off Hangar diagnostic and recovery scripts:

- `scripts/hangar-final.mjs`
- `scripts/hv-changelog.mjs`
- `scripts/hv-finish.mjs`
- `scripts/hv-urltest.mjs`
- `scripts/hv-versions.mjs`
- `scripts/hv.mjs`

Every reusable behavior from these scripts is already represented in
`hangar-upload-version.mjs`.

## Release documentation

Add `docs/RELEASING.md` as the canonical release runbook. It will cover:

1. Preconditions: clean version metadata, JDK 21, logged-in debug Chrome where
   required, and release notes/checksum assets.
2. Build and validation: `mvn clean verify`, selection of the obfuscated release
   JAR, checksum verification, and the Paper compatibility matrix.
3. GitHub: tag/release creation and asset verification.
4. SpigotMC: login check, description/icon maintenance, version publication,
   and verification of the displayed version and download link.
5. Modrinth: draft creation/version upload, explicit submission for review, and
   public API/search verification after approval.
6. Hangar: the proven external-URL wizard path, with the API publisher listed as
   an optional alternative.
7. Post-release audit: public URLs and authoritative sources for GitHub asset
   downloads, Modrinth project/version downloads, SpigotMC totals, Hangar
   views/downloads, and bStats server/player reporting.
8. Failure handling: stop after a failed channel, inspect public state before
   retrying, and never rerun a creation command blindly.

The README will link to the runbook from a short maintainer-facing release
section; user-facing installation content will remain unchanged.

## Script changes

Only targeted correctness and usability fixes are in scope:

- Ensure command help matches commands that are actually dispatched.
- Remove release-specific output text where a script already accepts a version
  argument.
- Fail clearly when expected form fields, buttons, or final redirects are
  missing instead of printing a success-shaped message.
- Do not store or print access tokens in documentation, committed files, or
  normal command output.

No marketplace metadata, published release, authentication setting, or remote
project will be changed as part of this cleanup.

## Verification

- Run `node --check` on every retained `.mjs` script.
- Run repository production-hygiene tests that cover publisher behavior.
- Run `mvn test` with JDK 21 to detect documentation/script hygiene regressions.
- Confirm removed script names are not referenced anywhere in tracked files.
- Confirm the release runbook's public links respond and that its commands match
  each script's command dispatcher.
- Review `git diff --check` and the final diff, preserving unrelated worktree
  changes.

## Success criteria

- A maintainer can publish a future version without reading the 1.4.0 debugging
  transcript.
- The documented workflow never treats a draft or uploaded version as public
  until an unauthenticated public check succeeds.
- Only reusable release tools remain in `scripts/`.
- All retained scripts parse, relevant tests pass, and unrelated user changes
  remain untouched.
