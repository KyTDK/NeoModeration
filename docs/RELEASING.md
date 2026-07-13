# Releasing NeoModeration

This is the canonical maintainer workflow. Run commands from the repository
root and replace only values explicitly shown as variables or placeholders.

## Rules

- Use JDK 21 and release only from a clean, reviewed commit.
- Keep `MODRINTH_TOKEN` and `HANGAR_API_KEY` in the environment. Never put
  credentials on a command line, in a file committed to Git, or in logs.
- Publish GitHub first. The GitHub release and its JAR must be public before
  Hangar's browser uploader can use the JAR URL.
- SpigotMC and the default Hangar browser upload require a signed-in debug
  Chrome on port 9223. Keep that browser profile private.
- Treat a publisher's successful exit as evidence that its operation completed,
  not proof that the result is publicly visible. Complete the public audit.
- A Modrinth upload is a draft. A maintainer must submit for review in the
  Modrinth UI; until the public API returns it, the project is unpublished.
- `scripts/hangar-entercode.mjs` is account setup only. Do not run it as part
  of a normal release.

## 1. Prepare and verify

Start from a clean checkout at the commit to release. Prepare release notes and
Spigot-formatted notes at the paths below, then copy the complete block. It runs
in a child Bash so failures stop the block without changing the caller's shell;
`pipefail` also makes a failed checksum stage fail the block.

```bash
bash <<'RELEASE_PREPARE'
set -euo pipefail
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"
JAR="target/NeoModeration-${VERSION}.jar"
RELEASE_NOTES="/tmp/NeoModeration-${VERSION}-release-notes.md"
SPIGOT_NOTES="/tmp/NeoModeration-${VERSION}-spigot-notes.txt"
CHECKSUMS="docs/releases/NeoModeration-${VERSION}-SHA256SUMS.txt"

git status --short
mvn clean verify
python3 scripts/version-matrix-verify.py "$JAR"
test -s "$JAR"
shasum -a 256 "$JAR" | sed 's#  target/#  #' | tee "$CHECKSUMS"
RELEASE_PREPARE
```

The Paper matrix command starts local Paper servers in Docker and verifies the
supported representative versions. Review its result report before continuing.
Review the release notes and checksum, commit any intended release metadata,
and ensure `git status --short` is empty.

## 2. Publish GitHub first

Create and push the release tag, then publish the JAR and checksum file:

```bash
bash <<'RELEASE_GITHUB'
set -euo pipefail
VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"
JAR="target/NeoModeration-${VERSION}.jar"
RELEASE_NOTES="/tmp/NeoModeration-${VERSION}-release-notes.md"
CHECKSUMS="docs/releases/NeoModeration-${VERSION}-SHA256SUMS.txt"

git tag -a "v${VERSION}" -m "NeoModeration ${VERSION}"
git push origin "v${VERSION}"
gh release create "v${VERSION}" "$JAR" "$CHECKSUMS" \
  --repo KyTDK/NeoModeration \
  --verify-tag \
  --title "NeoModeration ${VERSION}" \
  --notes-file "$RELEASE_NOTES"
RELEASE_GITHUB
```

Before any marketplace upload, prove that the release and exact JAR are public
and compare the published checksum with the local build:

```bash
bash <<'RELEASE_CHECKSUM'
set -euo pipefail
VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"
JAR="target/NeoModeration-${VERSION}.jar"

curl -fL "https://github.com/KyTDK/NeoModeration/releases/download/v${VERSION}/NeoModeration-${VERSION}.jar" \
  -o "/tmp/NeoModeration-${VERSION}.jar"
test "$(shasum -a 256 "$JAR" | awk '{print $1}')" = \
  "$(shasum -a 256 "/tmp/NeoModeration-${VERSION}.jar" | awk '{print $1}')"
RELEASE_CHECKSUM
```

Do not continue to Hangar if the download or checksum comparison fails.

## 3. Publish SpigotMC

Launch a dedicated Chrome profile on debug port 9223, then sign in to SpigotMC
in that Chrome window and solve any Cloudflare challenge:

```bash
bash <<'SPIGOT_PUBLISH'
set -euo pipefail
VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"
JAR="target/NeoModeration-${VERSION}.jar"
SPIGOT_NOTES="/tmp/NeoModeration-${VERSION}-spigot-notes.txt"

open -na "Google Chrome" --args \
  --remote-debugging-port=9223 \
  --user-data-dir="$HOME/.neomoderation-release-chrome"
export SPIGOT_CDP_PORT=9223
node scripts/spigot-publish.mjs check
node scripts/spigot-publish.mjs version "$JAR" "$VERSION" "$SPIGOT_NOTES"
SPIGOT_PUBLISH
```

The retained SpigotMC publisher dispatch is:

```text
check | describe <bbcode.txt> [--banner <png>] | tagline <text> | icon <png> | version <jar> <ver> <notes.txt>
```

Use these metadata mutations only when their inputs were reviewed. Assign real
paths/text first so every command remains safe to copy and edit:

```sh
SPIGOT_DESCRIPTION="/tmp/neomoderation-description.bbcode"
SPIGOT_BANNER="media/banner.png"
SPIGOT_TAGLINE="Automatic chat moderation for Minecraft"
SPIGOT_ICON="media/icon-spigot.png"
node scripts/spigot-publish.mjs describe "$SPIGOT_DESCRIPTION" --banner "$SPIGOT_BANNER"
node scripts/spigot-publish.mjs tagline "$SPIGOT_TAGLINE"
node scripts/spigot-publish.mjs icon "$SPIGOT_ICON"
```

## 4. Upload and submit Modrinth

Supply the authenticated API token through the environment. The publish
contract is exactly `publish <jar> <version>`:

```bash
bash <<'MODRINTH_PUBLISH'
set -euo pipefail
VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"
JAR="target/NeoModeration-${VERSION}.jar"
test -n "${MODRINTH_TOKEN:-}" &&
  node scripts/modrinth-publish.mjs publish "$JAR" "$VERSION"
MODRINTH_PUBLISH
```

If the token check fails, stop and load `MODRINTH_TOKEN` from the maintainer's
secret store before retrying this block. The `&&` is the execution gate: it
prevents the publisher's automatic browser PAT creation path from running when
the environment token is empty, without exiting an interactive shell.

The retained diagnostic/setup dispatch is:

```text
open | check | inspect | patdebug | patdom | publish <jar> <version>
```

The publisher creates or updates the project as a draft and uploads the version.
Open `https://modrinth.com/plugin/neomoderation/settings`, inspect the draft,
and submit for review. Upload success does not make the project public. A `404`
from `https://api.modrinth.com/v2/project/neomoderation` explicitly means the
project remains unpublished; it is not a successful public audit.

## 5. Publish Hangar

The proven default is the browser uploader. First confirm the GitHub JAR URL is
public, then sign in to Hangar in the same debug Chrome on port 9223 and leave a
Hangar tab open:

```bash
bash <<'HANGAR_BROWSER_PUBLISH'
set -euo pipefail
VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"

curl -fIL "https://github.com/KyTDK/NeoModeration/releases/download/v${VERSION}/NeoModeration-${VERSION}.jar"
export CDP_PORT=9223
node scripts/hangar-upload-version.mjs "$VERSION"
HANGAR_BROWSER_PUBLISH
```

If Hangar requires account email verification during initial setup only, use:

```sh
test -n "${HANGAR_CODE:-}" # load the emailed code into the environment first
node scripts/hangar-entercode.mjs "$HANGAR_CODE"
unset HANGAR_CODE
```

Do not put `hangar-entercode` in the per-release workflow. It submits an account
verification code and is not a version publisher.

The Hangar API publisher is retained as an optional alternative, not the proven
default. Use it only when a maintainer has intentionally created an API key with
the documented project/version permissions:

```bash
bash <<'HANGAR_API_PUBLISH'
set -euo pipefail
VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"
JAR="target/NeoModeration-${VERSION}.jar"
test -n "${HANGAR_API_KEY:-}" &&
  node scripts/hangar-publish.mjs publish "$JAR" "$VERSION"
HANGAR_API_PUBLISH
```

Its dispatch is exactly `publish <jar> <version>`.

## 6. Audit the public release

These commands are read-only. Run them after every publisher and inspect the
returned release/version data rather than relying only on HTTP status:

```bash
bash <<'RELEASE_AUDIT'
set -euo pipefail
audit_failed=0

github_url='https://api.github.com/repos/KyTDK/NeoModeration/releases'
http_code="$(curl -sS -o /tmp/neomoderation-github.json -w '%{http_code}' "$github_url")" || http_code=000
printf '%s %s\n' "$http_code" "$github_url"
if [[ "$http_code" == 200 ]]; then
  jq . /tmp/neomoderation-github.json >/dev/null || audit_failed=1
else
  audit_failed=1
fi

modrinth_url='https://api.modrinth.com/v2/project/neomoderation'
http_code="$(curl -sS -o /tmp/neomoderation-modrinth.json -w '%{http_code}' "$modrinth_url")" || http_code=000
printf '%s %s\n' "$http_code" "$modrinth_url"
case "$http_code" in
  200)
    echo 'PUBLIC: Modrinth project is visible'
    jq . /tmp/neomoderation-modrinth.json >/dev/null || audit_failed=1
    ;;
  404)
    echo 'UNPUBLISHED: Modrinth review/publication remains outstanding' >&2
    audit_failed=1
    ;;
  *)
    echo "Modrinth audit failed: HTTP $http_code" >&2
    audit_failed=1
    ;;
esac

hangar_url='https://hangar.papermc.io/api/v1/projects/KyTDK/NeoModeration'
http_code="$(curl -sS -o /tmp/neomoderation-hangar.json -w '%{http_code}' "$hangar_url")" || http_code=000
printf '%s %s\n' "$http_code" "$hangar_url"
if [[ "$http_code" == 200 ]]; then
  jq . /tmp/neomoderation-hangar.json >/dev/null || audit_failed=1
else
  audit_failed=1
fi

bstats_url='https://bstats.org/plugin/bukkit/NeoModeration/32542'
http_code="$(curl -sS -L -o /tmp/neomoderation-bstats.html -w '%{http_code}' "$bstats_url")" || http_code=000
printf '%s %s\n' "$http_code" "$bstats_url"
if [[ "$http_code" != 200 ]]; then
  audit_failed=1
fi

if (( audit_failed != 0 )); then
  echo 'Public release audit has outstanding or failed channels.' >&2
fi
exit "$audit_failed"
RELEASE_AUDIT
```

Confirm GitHub exposes tag `v${VERSION}` and the named JAR asset, Modrinth
exposes the reviewed project and version, and Hangar exposes the new version and
public GitHub download. Also open the marketplace pages as a signed-out user.

The plugin reports bStats custom charts named `moderation_mode`,
`cloud_enabled`, and `chat_censor`. Do not include any custom chart in a release
audit until that chart appears in the bStats dashboard metadata. Check the saved
dashboard response before recording them:

```sh
rg -n 'moderation_mode|cloud_enabled|chat_censor' /tmp/neomoderation-bstats.html
```

An absent chart is “not yet visible,” not zero activity. Record the audit time,
HTTP statuses, visible marketplace versions, and checksum result in the release
notes or maintainer log.

## Failure recovery

- Stop on the first failed command. Preserve its output without preserving
  credentials, and identify whether the remote operation happened before retrying.
- Never blindly rerun a publisher: first query its public page/API and inspect
  the authenticated dashboard for a draft or partial version. Avoid duplicate
  marketplace versions.
- If GitHub is absent or its JAR/checksum is wrong, repair or replace that release
  before SpigotMC, Modrinth, or Hangar. Hangar must never point at a private or
  mismatched GitHub asset.
- If SpigotMC or Hangar browser automation cannot find a field or button, keep
  the page open, inspect the UI/login state in debug Chrome, and do not treat the
  failed exit as publication.
- If Modrinth returns `404`, treat it as unpublished. Inspect the draft, resolve
  review feedback, submit for review again when appropriate, and wait for the
  public API to return `200` before marking it public.
- If a command succeeds but the public audit fails, report the channel as pending
  or failed. Command success is not proof of public visibility.
