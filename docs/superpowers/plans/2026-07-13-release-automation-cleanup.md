# Release Automation Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 1.4.0 publishing probes with reusable, guarded release tools and one canonical release runbook.

**Architecture:** Keep each marketplace publisher independent because authentication, review, and failure semantics differ by channel. Use static production-hygiene tests to enforce the release-kit boundary, then document an ordered GitHub → SpigotMC → Modrinth → Hangar workflow with unauthenticated verification after every external mutation.

**Tech Stack:** Java 21, Maven/JUnit 5, Node.js ES modules, Chrome DevTools Protocol, Playwright-over-CDP, GitHub/Modrinth/Hangar REST APIs.

## Global Constraints

- Do not change marketplace metadata, published releases, authentication settings, or remote projects.
- Preserve unrelated worktree changes.
- Keep `scripts/spigot-publish.mjs`, `scripts/modrinth-publish.mjs`, `scripts/cdp-lib.mjs`, `scripts/hangar-upload-version.mjs`, `scripts/hangar-publish.mjs`, and `scripts/hangar-entercode.mjs`.
- Remove only `scripts/hangar-final.mjs`, `scripts/hv-changelog.mjs`, `scripts/hv-finish.mjs`, `scripts/hv-urltest.mjs`, `scripts/hv-versions.mjs`, and `scripts/hv.mjs`.
- Never persist or print access tokens in documentation, committed files, or normal command output.
- Treat an uploaded Modrinth draft as unpublished until its public API endpoint succeeds unauthenticated.
- Use JDK 21 for Maven commands.

---

### Task 1: Define and enforce the reusable release-kit boundary

**Files:**
- Modify: `src/test/java/com/neomechanical/neomoderation/ProductionHygieneTest.java`
- Delete: `scripts/hangar-final.mjs`
- Delete: `scripts/hv-changelog.mjs`
- Delete: `scripts/hv-finish.mjs`
- Delete: `scripts/hv-urltest.mjs`
- Delete: `scripts/hv-versions.mjs`
- Delete: `scripts/hv.mjs`
- Add to version control: `media/icon-spigot.png`

**Interfaces:**
- Consumes: the approved retained/removed file lists from the design spec.
- Produces: `releaseKitContainsOnlyReusableToolsAndAssets()`, a JUnit guardrail that future changes must satisfy.

- [ ] **Step 1: Write the failing release-kit hygiene test**

Add this test to `ProductionHygieneTest`:

```java
    @Test
    void releaseKitContainsOnlyReusableToolsAndAssets() throws IOException {
        List<Path> reusable = List.of(
                Path.of("scripts/spigot-publish.mjs"),
                Path.of("scripts/modrinth-publish.mjs"),
                Path.of("scripts/cdp-lib.mjs"),
                Path.of("scripts/hangar-upload-version.mjs"),
                Path.of("scripts/hangar-publish.mjs"),
                Path.of("scripts/hangar-entercode.mjs"),
                Path.of("media/icon-spigot.png")
        );
        List<Path> probes = List.of(
                Path.of("scripts/hangar-final.mjs"),
                Path.of("scripts/hv-changelog.mjs"),
                Path.of("scripts/hv-finish.mjs"),
                Path.of("scripts/hv-urltest.mjs"),
                Path.of("scripts/hv-versions.mjs"),
                Path.of("scripts/hv.mjs")
        );

        assertTrue(reusable.stream().allMatch(Files::isRegularFile),
                () -> "Reusable release files are missing: "
                        + reusable.stream().filter(path -> !Files.isRegularFile(path)).toList());
        assertTrue(probes.stream().noneMatch(Files::exists),
                () -> "One-off Hangar probes must be removed: "
                        + probes.stream().filter(Files::exists).toList());
    }
```

- [ ] **Step 2: Run the test and confirm it fails on the probes**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -q -Dtest=ProductionHygieneTest#releaseKitContainsOnlyReusableToolsAndAssets test
```

Expected: FAIL with `One-off Hangar probes must be removed` and the six probe paths.

- [ ] **Step 3: Remove the probes and retain the published icon**

Delete exactly the six probe files listed under this task. Leave
`media/icon-spigot.png` unchanged and include it in the commit. Do not delete
any retained publisher or `cdp-lib.mjs`.

- [ ] **Step 4: Run the focused test and parser checks**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -q -Dtest=ProductionHygieneTest#releaseKitContainsOnlyReusableToolsAndAssets test
for script in scripts/*.mjs; do node --check "$script"; done
```

Expected: the test passes and every parser check exits 0.

- [ ] **Step 5: Commit the release-kit boundary**

```bash
git add src/test/java/com/neomechanical/neomoderation/ProductionHygieneTest.java \
  media/icon-spigot.png scripts/cdp-lib.mjs scripts/hangar-entercode.mjs \
  scripts/hangar-publish.mjs scripts/hangar-upload-version.mjs
git commit -m "chore: consolidate release tooling"
```

The six removed probes were untracked debugging files, so deleting them does
not add deletion entries to the commit; the hygiene test records their absence.

---

### Task 2: Harden publisher success and failure reporting

**Files:**
- Modify: `src/test/java/com/neomechanical/neomoderation/ProductionHygieneTest.java`
- Modify: `scripts/modrinth-publish.mjs`
- Modify: `scripts/spigot-publish.mjs`
- Modify: `scripts/hangar-upload-version.mjs`
- Modify: `scripts/hangar-entercode.mjs`

**Interfaces:**
- Consumes: the retained script set enforced by Task 1.
- Produces: version-parameterized Modrinth output and explicit failures for missing SpigotMC/Hangar controls or unsuccessful redirects.

- [ ] **Step 1: Write static publisher-safety tests**

Add this test to `ProductionHygieneTest`:

```java
    @Test
    void publishersUseParameterizedOutputAndExplicitFailureChecks() throws IOException {
        String modrinth = Files.readString(Path.of("scripts/modrinth-publish.mjs"));
        String spigot = Files.readString(Path.of("scripts/spigot-publish.mjs"));
        String hangar = Files.readString(Path.of("scripts/hangar-upload-version.mjs"));

        assertTrue(!modrinth.contains("Uploaded version 1.4.0."),
                "Modrinth output must use the requested version");
        assertTrue(!modrinth.contains("console.log(JSON.stringify({ found: !!token"),
                "Modrinth must not expose bearer tokens through a CLI command");
        assertTrue(spigot.contains("throw new Error(`Save button not found"),
                "Spigot mutations must fail when their submit control is missing");
        assertTrue(hangar.contains("if (!urlLen) throw new Error"),
                "Hangar must stop when the external URL was not entered");
        assertTrue(hangar.contains("if (!published) throw new Error"),
                "Hangar must stop when creation did not leave the new-version page");
    }
```

- [ ] **Step 2: Run the focused test and confirm it fails**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -q -Dtest=ProductionHygieneTest#publishersUseParameterizedOutputAndExplicitFailureChecks test
```

Expected: FAIL on the hard-coded Modrinth output and missing explicit guards.

- [ ] **Step 3: Remove Modrinth token output and parameterize status**

Delete `cmdToken()` completely. Keep dispatch limited to `open`, `check`,
`inspect`, `patdebug`, `patdom`, and `publish`. Use this fallback help:

```javascript
console.log("commands: open | check | inspect | patdebug | patdom | publish <token> <jar> <version>");
```

Replace the hard-coded upload log with:

```javascript
console.log(`Uploaded version ${version} as a draft.`);
```

Keep the existing review-submission URL message.

- [ ] **Step 4: Make SpigotMC mutations fail when controls are absent**

Replace success-shaped ternary logs in `cmdDescribe`, `cmdTagline`, `cmdIcon`,
and `cmdVersion` with these guard/success pairs:

```javascript
if (!saved) throw new Error(`Save button not found for ${BASE}/edit`);
console.log(`Description saved (fields set: ${n}).`);
```

```javascript
if (!saved) throw new Error(`Save button not found for ${BASE}/edit`);
console.log(`Tag line set to: ${tagline}`);
```

```javascript
if (!saved) throw new Error(`Save Changes button not found for ${BASE}/icon`);
console.log("Icon saved.");
```

```javascript
if (!saved) throw new Error(`Save Update button not found for ${BASE}/add-version`);
console.log(`Posted version ${versionString}.`);
```

- [ ] **Step 5: Guard required Hangar wizard transitions**

Require `urlLen` and `verLen` with:

```javascript
if (!urlLen) throw new Error("Hangar external URL field was not found or remained empty.");
if (!verLen) throw new Error("Hangar version field was not found or remained empty.");
```

Require each `Next` click, naming the artifact, artifact-data, or dependencies
step in its error. Require `editorBox` before typing and require the Create
click. Compute publication and fail explicitly:

```javascript
const published = Boolean(after) && !after.includes("/versions/new");
await sleep(1000);
await p.screenshot(SHOT).catch(() => {});
if (!published) throw new Error(`Hangar version creation did not complete; current URL: ${after || "unknown"}`);
console.log(JSON.stringify({ urlSet, published, finalUrl: after }, null, 1));
```

Use `try/finally` so `p.close()` executes when a guard throws.

- [ ] **Step 6: Harden Hangar account verification**

After filling and submitting, add:

```javascript
if (filled === "no-field") throw new Error("Hangar verification-code field was not found.");
if (!submitted) throw new Error("Hangar Verify Code button was not found.");
```

Keep verification codes out of output and close the page in `finally`.

- [ ] **Step 7: Run focused tests and parser checks**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -q -Dtest=ProductionHygieneTest test
for script in scripts/*.mjs; do node --check "$script"; done
```

Expected: all production-hygiene tests pass and every script parses.

- [ ] **Step 8: Commit publisher hardening**

```bash
git add src/test/java/com/neomechanical/neomoderation/ProductionHygieneTest.java \
  scripts/modrinth-publish.mjs scripts/spigot-publish.mjs \
  scripts/hangar-upload-version.mjs scripts/hangar-entercode.mjs
git commit -m "fix: harden release publishers"
```

---

### Task 3: Add the canonical release runbook

**Files:**
- Create: `docs/RELEASING.md`
- Modify: `README.md`
- Modify: `src/test/java/com/neomechanical/neomoderation/ProductionHygieneTest.java`

**Interfaces:**
- Consumes: final commands and failure semantics from Task 2.
- Produces: a canonical maintainer workflow and `releaseRunbookCoversEveryPublicationAndAuditChannel()`.

- [ ] **Step 1: Write the failing runbook coverage test**

```java
    @Test
    void releaseRunbookCoversEveryPublicationAndAuditChannel() throws IOException {
        Path runbook = Path.of("docs/RELEASING.md");
        assertTrue(Files.isRegularFile(runbook), "Canonical release runbook is missing");

        String text = Files.readString(runbook);
        List<String> required = List.of(
                "mvn clean verify",
                "scripts/version-matrix-verify.py",
                "scripts/spigot-publish.mjs",
                "scripts/modrinth-publish.mjs",
                "submit for review",
                "api.modrinth.com/v2/project/neomoderation",
                "scripts/hangar-upload-version.mjs",
                "api.github.com/repos/KyTDK/NeoModeration/releases",
                "hangar.papermc.io/api/v1/projects/KyTDK/NeoModeration",
                "bstats.org/plugin/bukkit/NeoModeration/32542"
        );
        assertTrue(required.stream().allMatch(text::contains),
                () -> "Release runbook is missing: "
                        + required.stream().filter(value -> !text.contains(value)).toList());
        assertTrue(Files.readString(Path.of("README.md")).contains("docs/RELEASING.md"),
                "README must link to the maintainer release runbook");
    }
```

- [ ] **Step 2: Run the focused test and confirm it fails**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -q -Dtest=ProductionHygieneTest#releaseRunbookCoversEveryPublicationAndAuditChannel test
```

Expected: FAIL with `Canonical release runbook is missing`.

- [ ] **Step 3: Write `docs/RELEASING.md`**

Use these exact top-level sections:

```markdown
# Releasing NeoModeration

## Rules
## 1. Prepare and verify
## 2. Publish GitHub first
## 3. Publish SpigotMC
## 4. Upload and submit Modrinth
## 5. Publish Hangar
## 6. Audit the public release
## Failure recovery
```

Include executable commands for JDK 21, `mvn clean verify`, Paper matrix,
checksum comparison, every retained publisher, and read-only GitHub/Modrinth/
Hangar audit APIs. State that GitHub must be public before Hangar uses its JAR,
SpigotMC and Hangar require debug Chrome on port 9223, Modrinth upload produces
a draft that must be submitted for review, command success is not proof of
public visibility, and bStats custom charts must appear in dashboard metadata
before inclusion in an audit.

- [ ] **Step 4: Link the runbook from README**

Append:

```markdown
## Maintainers

The build, marketplace publishing, review, and post-release verification
process is documented in [docs/RELEASING.md](docs/RELEASING.md).
```

- [ ] **Step 5: Run the test and public link checks**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -q -Dtest=ProductionHygieneTest#releaseRunbookCoversEveryPublicationAndAuditChannel test
for url in \
  https://api.github.com/repos/KyTDK/NeoModeration/releases \
  https://api.modrinth.com/v2/project/neomoderation \
  https://hangar.papermc.io/api/v1/projects/KyTDK/NeoModeration \
  https://bstats.org/plugin/bukkit/NeoModeration/32542; do
  status=$(curl -sS -o /dev/null -w '%{http_code}' "$url")
  printf '%s %s\n' "$status" "$url"
done
```

Expected: JUnit passes; GitHub, Hangar, and bStats return 200. Modrinth may
return 404 until review completes and must remain documented as unpublished.

- [ ] **Step 6: Commit the runbook**

```bash
git add docs/RELEASING.md README.md \
  src/test/java/com/neomechanical/neomoderation/ProductionHygieneTest.java
git commit -m "docs: add marketplace release runbook"
```

---

### Task 4: Verify the complete cleanup

**Files:**
- Verify only; modify only a Task 1–3 file if its verification fails.

**Interfaces:**
- Consumes: all Task 1–3 deliverables.
- Produces: evidence that the cleanup parses, passes tests, preserves live releases, and contains no unintended scope.

- [ ] **Step 1: Run Node parser checks**

```bash
for script in scripts/*.mjs; do node --check "$script"; done
```

Expected: every script exits 0.

- [ ] **Step 2: Run Maven tests with JDK 21**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn test
```

Expected: BUILD SUCCESS and all tests pass.

- [ ] **Step 3: Check removed references and formatting**

```bash
rg -n "hangar-final|hv-changelog|hv-finish|hv-urltest|hv-versions|scripts/hv\\.mjs" \
  README.md docs scripts src || true
git diff --check HEAD~3..HEAD
```

Expected: obsolete names appear only in historical design/plan documents;
`git diff --check` emits nothing.

- [ ] **Step 4: Verify public state read-only**

```bash
curl -fsSL -H 'Accept: application/vnd.github+json' \
  https://api.github.com/repos/KyTDK/NeoModeration/releases/latest | \
  jq '{tag_name, assets: [.assets[] | {name, download_count}]}'
curl -fsSL https://hangar.papermc.io/api/v1/projects/KyTDK/NeoModeration | \
  jq '{visibility, stats}'
curl -sS -o /dev/null -w '%{http_code}\\n' \
  https://api.modrinth.com/v2/project/neomoderation
```

Expected: GitHub reports `v1.4.0`; Hangar reports `public`; Modrinth returns 200
only after public approval and otherwise remains the documented outstanding
channel.

- [ ] **Step 5: Review repository scope**

```bash
git status --short
git log -4 --oneline --decorate
git diff HEAD~3..HEAD --stat
```

Expected: only approved release-tooling, documentation, asset, and hygiene-test
changes appear. No live publishing command was executed.
