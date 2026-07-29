package com.neomechanical.neomoderation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionHygieneTest {
    @Test
    void modrinthPublisherUsesDocumentedDraftContractAndSafeLookupSemantics() throws Exception {
        NodeResult contract = runNodeAssertions("""
                import assert from "node:assert/strict";
                import {
                  buildModrinthVersionData,
                  classifyModrinthProjectLookup,
                  expectedModrinthJarName,
                  selectModrinthDraftForPromotion,
                  validateModrinthDraftVersion
                } from "./scripts/modrinth-publish.mjs";
                import {
                  hangarPaperVersions,
                  isSupportedMinecraftVersion,
                  supportedMinecraftVersions
                } from "./scripts/release-compatibility.mjs";

                const payload = buildModrinthVersionData({
                  projectId: "project-123",
                  version: "2.0.0",
                  changelog: "Release notes",
                  gameVersions: ["1.21.8"],
                  loaders: ["paper"]
                });
                assert.deepEqual(payload, {
                  project_id: "project-123",
                  file_parts: ["file"],
                  version_number: "2.0.0",
                  name: "NeoModeration 2.0.0",
                  changelog: "Release notes",
                  dependencies: [],
                  game_versions: ["1.21.8"],
                  version_type: "release",
                  loaders: ["paper"],
                  featured: true,
                  status: "draft"
                });
                assert.equal(classifyModrinthProjectLookup(200), "exists");
                assert.equal(classifyModrinthProjectLookup(404), "missing");
                assert.throws(() => classifyModrinthProjectLookup(401), /HTTP 401/);
                assert.throws(() => classifyModrinthProjectLookup(500), /HTTP 500/);
                assert.doesNotThrow(() => validateModrinthDraftVersion({
                  id: "version-123",
                  project_id: "project-123",
                  version_number: "2.0.0",
                  status: "draft"
                }, "project-123", "2.0.0"));
                assert.throws(() => validateModrinthDraftVersion({
                  id: "version-123",
                  project_id: "project-123",
                  version_number: "1.9.0",
                  status: "draft"
                }, "project-123", "2.0.0"), /requested version/);
                assert.throws(() => validateModrinthDraftVersion({
                  id: "version-123",
                  project_id: "project-123",
                  version_number: "2.0.0",
                  status: "listed"
                }, "project-123", "2.0.0"), /draft/);
                assert.equal(expectedModrinthJarName("2.0.0"), "NeoModeration-2.0.0-modrinth.jar");
                assert.equal(selectModrinthDraftForPromotion([{
                  id: "version-123",
                  project_id: "project-123",
                  version_number: "2.0.0",
                  status: "draft",
                  files: [{ primary: true, hashes: { sha512: "expected-sha512" } }]
                }], "project-123", "2.0.0", "expected-sha512").id, "version-123");
                assert.throws(() => selectModrinthDraftForPromotion([{
                  id: "version-123",
                  project_id: "project-123",
                  version_number: "2.0.0",
                  status: "listed",
                  files: [{ primary: true, hashes: { sha512: "expected-sha512" } }]
                }], "project-123", "2.0.0", "expected-sha512"), /draft/);
                assert.throws(() => selectModrinthDraftForPromotion([{
                  id: "version-123",
                  project_id: "project-123",
                  version_number: "2.0.0",
                  status: "draft",
                  files: [{ primary: true, hashes: { sha512: "different" } }]
                }], "project-123", "2.0.0", "expected-sha512"), /SHA-512/);
                assert.equal(isSupportedMinecraftVersion("1.18.2"), true);
                assert.equal(isSupportedMinecraftVersion("1.21.11"), true);
                assert.equal(isSupportedMinecraftVersion("1.18.1"), false);
                assert.equal(isSupportedMinecraftVersion("1.22"), false);
                assert.deepEqual(
                  supportedMinecraftVersions(["1.18.1", "1.18.2", "1.21.11", "1.22"]),
                  ["1.18.2", "1.21.11"]
                );
                assert.deepEqual(hangarPaperVersions([
                  { version: "1.18", subVersions: ["1.18.2", "1.18.1"] },
                  { version: "1.21", subVersions: ["1.21.11", "1.21"] },
                  { version: "26.1", subVersions: ["26.1.1"] }
                ]), ["1.18.2", "1.21.11", "1.21"]);
                """);
        assertTrue(contract.exitCode() == 0,
                () -> "Modrinth contract fixture failed:\n" + contract.output());

        String modrinth = Files.readString(Path.of("scripts/modrinth-publish.mjs"));
        assertTrue(modrinth.contains("if (!token) throw new Error(\"Set MODRINTH_TOKEN in env.\");")
                        && !modrinth.contains("async function createPat")
                        && !modrinth.contains("Creating a scoped publishing token"),
                "Modrinth publishing must require the environment token without creating a PAT");
        assertTrue(!modrinth.contains("api(token, \"/user\")")
                        && !modrinth.contains("Token valid ("),
                "Modrinth publishing must not require unrelated user-read permission");
        assertTrue(!modrinth.contains("\"folia\"")
                        && modrinth.contains("supportedMinecraftVersions("),
                "Modrinth must advertise only tested loaders and Minecraft versions");
    }

    @Test
    void modrinthReleaseUsesAReviewableNonObfuscatedArtifact() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        String runbook = Files.readString(Path.of("docs/RELEASING.md"));
        String publisher = Files.readString(Path.of("scripts/modrinth-publish.mjs"));

        assertTrue(pom.contains("<id>modrinth</id>")
                        && pom.contains("<proguard.skip>true</proguard.skip>")
                        && pom.contains("NeoModeration-${project.version}-modrinth"),
                "The Modrinth Maven profile must produce a clearly named non-obfuscated artifact");
        assertTrue(runbook.contains("mvn -Pmodrinth clean verify")
                        && runbook.contains("target/NeoModeration-${VERSION}-modrinth.jar")
                        && runbook.contains("Modrinth rejects the normal ProGuard-obfuscated release JAR")
                        && runbook.contains("modrinth-publish.mjs promote \"$JAR\" \"$VERSION\""),
                "The runbook must build and explain the Modrinth-specific reviewable artifact");
        assertTrue(publisher.contains("expectedModrinthJarName(version)")
                        && publisher.contains("path.basename(jar) !== expectedJar")
                        && publisher.contains("cmd === \"promote\"")
                        && publisher.contains("`/version_file/${expectedSha512}?algorithm=sha512`")
                        && publisher.contains("body: JSON.stringify({ status: \"listed\" })"),
                "The Modrinth publisher must reject the normal obfuscated release JAR");
    }

    @Test
    void browserPublishersRequireExactInputsAndDefinitiveSuccess() throws Exception {
        NodeResult contract = runNodeAssertions("""
                import assert from "node:assert/strict";
                import { isSpigotVersionSubmissionSuccessful } from "./scripts/spigot-publish.mjs";
                import {
                  classifyHangarProjectLookup,
                  isExactHangarVersionUrl
                } from "./scripts/hangar-publish.mjs";

                assert.equal(isSpigotVersionSubmissionSuccessful(
                  "https://www.spigotmc.org/resources/neomoderation.136721/add-version",
                  "NeoModeration 2.0.0",
                  "2.0.0"), false);
                assert.equal(isSpigotVersionSubmissionSuccessful(
                  "https://www.spigotmc.org/resources/neomoderation.136721/updates/1234/",
                  "Update posted",
                  "2.0.0"), false);
                assert.equal(isSpigotVersionSubmissionSuccessful(
                  "https://www.spigotmc.org/resources/neomoderation.136721/updates/1234/",
                  "NeoModeration 2.0.0",
                  "2.0.0"), true);
                assert.equal(classifyHangarProjectLookup(200), "exists");
                assert.equal(classifyHangarProjectLookup(404), "missing");
                assert.throws(() => classifyHangarProjectLookup(403), /HTTP 403/);
                assert.throws(() => classifyHangarProjectLookup(503), /HTTP 503/);
                assert.equal(isExactHangarVersionUrl(
                  "https://hangar.papermc.io/KyTDK/NeoModeration/versions/2.0.0",
                  "KyTDK", "NeoModeration", "2.0.0"), true);
                assert.equal(isExactHangarVersionUrl(
                  "https://hangar.papermc.io/KyTDK/NeoModeration/versions",
                  "KyTDK", "NeoModeration", "2.0.0"), false);
                assert.equal(isExactHangarVersionUrl(
                  "https://hangar.papermc.io/KyTDK/NeoModeration/versions/1.9.0",
                  "KyTDK", "NeoModeration", "2.0.0"), false);
                """);
        assertTrue(contract.exitCode() == 0,
                () -> "Browser/API publisher contract fixture failed:\n" + contract.output());

        String spigot = Files.readString(Path.of("scripts/spigot-publish.mjs"));
        String hangarUpload = Files.readString(Path.of("scripts/hangar-upload-version.mjs"));
        String hangarEnterCode = Files.readString(Path.of("scripts/hangar-entercode.mjs"));
        assertTrue(spigot.contains("if (!notesFields) throw new Error")
                        && spigot.contains("if (!notesPopulated) throw new Error")
                        && spigot.contains("if (!submissionSuccessful) throw new Error"),
                "Spigot version publishing must require populated notes and definitive submission proof");
        assertTrue(hangarUpload.contains("if (urlValue !== jarUrl) throw new Error")
                        && hangarUpload.contains("if (versionValue !== version) throw new Error")
                        && hangarUpload.contains("isExactHangarVersionUrl(after, OWNER, PROJECT, version)"),
                "Hangar browser publishing must verify exact inputs and the exact version URL");
        assertTrue(hangarEnterCode.contains("if (!accepted) throw new Error")
                        && !hangarEnterCode.contains("console.log(result);"),
                "Hangar verification-code submission must exit nonzero unless accepted");
    }

    @Test
    void releaseRunbookRejectsFalsePositiveAuditsAndUsesTwoStageChromeAuth() throws IOException {
        String text = Files.readString(Path.of("docs/RELEASING.md"));
        int auditStart = text.indexOf("bash <<'RELEASE_AUDIT'");
        int auditEnd = text.indexOf("RELEASE_AUDIT\n```", auditStart + 1);
        String audit = auditStart >= 0 && auditEnd > auditStart ? text.substring(auditStart, auditEnd) : "";

        assertTrue(audit.contains("releases/tags/v${VERSION}")
                        && !audit.contains("releases/latest"),
                "GitHub audit must inspect the requested tag rather than the latest release");
        assertTrue(audit.contains("unknownPlugin:false")
                        && audit.contains("plugin:{id:32542,name:\"NeoModeration\"")
                        && audit.contains("resolve(1, () => [[[")
                        && audit.contains("resolve(2, () => [[["),
                "bStats audit must require the NeoModeration identity and populated server/player series");

        int launch = text.indexOf("bash <<'SPIGOT_CHROME_LAUNCH'");
        int connectionCheck = text.indexOf("bash <<'SPIGOT_CONNECTION_CHECK'");
        int publish = text.indexOf("bash <<'SPIGOT_PUBLISH'");
        assertTrue(launch >= 0 && connectionCheck > launch && publish > connectionCheck
                        && text.substring(launch, connectionCheck).contains("open -na \"Google Chrome\"")
                        && !text.substring(launch, connectionCheck).contains("spigot-publish.mjs check")
                        && text.substring(connectionCheck, publish).contains("spigot-publish.mjs check"),
                "Spigot Chrome launch, interactive authentication, connection check, and publish must be separate stages");
    }

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

    @Test
    void publishersUseParameterizedOutputAndExplicitFailureChecks() throws IOException {
        String modrinth = Files.readString(Path.of("scripts/modrinth-publish.mjs"));
        String spigot = Files.readString(Path.of("scripts/spigot-publish.mjs"));
        String hangar = Files.readString(Path.of("scripts/hangar-upload-version.mjs"));
        String hangarEnterCode = Files.readString(Path.of("scripts/hangar-entercode.mjs"));

        assertTrue(modrinth.contains("console.log(`Uploaded version ${version} as a draft.`);"),
                "Modrinth output must use the requested version");
        assertTrue(!modrinth.contains("async function cmdToken"),
                "Modrinth must not expose a bearer-token output command");
        assertTrue(!modrinth.contains("publish <token>"),
                "Modrinth must not accept bearer tokens as positional arguments");
        assertTrue(!modrinth.contains("modrinth-publish.mjs pat ")
                        && modrinth.contains("node scripts/modrinth-publish.mjs publish <jar> <version>")
                        && modrinth.contains("*   MODRINTH_TOKEN")
                        && modrinth.contains("commands: open | check | status | inspect | patdebug | patdom | publish <jar> <version> | promote <jar> <version>"),
                "Modrinth usage and help must document the dispatched environment-token interface");
        List<String> modrinthCommands = List.of("open", "check", "status", "inspect", "patdebug", "patdom", "publish", "promote");
        assertTrue(modrinthCommands.stream().allMatch(command -> modrinth.contains("cmd === \"" + command + "\""))
                        && countOccurrences(modrinth, "cmd === \"") == modrinthCommands.size(),
                "Modrinth help and dispatch commands must agree");

        assertTrue(countOccurrences(spigot,
                        "if (!saved) throw new Error(`Save button not found for ${BASE}/edit`);") == 2,
                "Spigot description and tagline mutations must fail when Save is missing");
        assertTrue(spigot.contains("if (!saved) throw new Error(`Save Changes button not found for ${BASE}/icon`);")
                        && spigot.contains("if (!saved) throw new Error(`Save Update button not found for ${BASE}/add-version`);"),
                "Spigot icon and version mutations must fail when submit controls are missing");

        assertTrue(hangar.contains("if (!urlLen) throw new Error(\"Hangar external URL field was not found or remained empty.\");")
                        && hangar.contains("if (!verLen) throw new Error(\"Hangar version field was not found or remained empty.\");"),
                "Hangar must require the external URL and version fields");
        assertTrue(hangar.contains("if (!artifactNext) throw new Error(\"Hangar Next button was not found for the artifact step.\");")
                        && hangar.contains("if (!artifactDataNext) throw new Error(\"Hangar Next button was not found for the artifact-data step.\");")
                        && hangar.contains("if (!dependenciesNext) throw new Error(\"Hangar Next button was not found for the dependencies step.\");"),
                "Hangar must require every named Next-step transition");
        assertTrue(hangar.contains("if (!editorBox) throw new Error(\"Hangar changelog editor was not found.\");")
                        && hangar.contains("if (!created) throw new Error(\"Hangar Create button was not found.\");"),
                "Hangar must require the changelog editor and Create control");
        assertTrue(hangar.contains("if (!published) throw new Error")
                        && hangar.contains("} finally {\n  p.close();\n}"),
                "Hangar must stop when creation did not leave the new-version page");

        assertTrue(hangarEnterCode.contains("if (filled === \"no-field\") throw new Error(\"Hangar verification-code field was not found.\");")
                        && hangarEnterCode.contains("if (!submitted) throw new Error(\"Hangar Verify Code button was not found.\");"),
                "Hangar account verification must require the code field and submit control");
        assertTrue(hangarEnterCode.contains("} finally {\n  p.close();\n}"),
                "Hangar account verification must close its CDP page on failure");
    }

    @Test
    void productionSourcesDoNotDumpStackTracesOrWrapGenericRuntimeExceptions() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> violations;
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            violations = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(ProductionHygieneTest::scan)
                    .toList();
        }
        assertTrue(violations.isEmpty(), () -> "Production exception hygiene violations:\n" + String.join("\n", violations));
    }

    @Test
    void activeReleaseToolsDoNotEmbedStaleVersionsOrOmitSpigotTitle() throws IOException {
        List<Path> activeVerificationScripts = List.of(
                Path.of("scripts/version-matrix-verify.py")
        );
        List<String> staleVersions = activeVerificationScripts.stream()
                .filter(path -> {
                    try {
                        return Files.readString(path).contains("1.1.0");
                    } catch (IOException e) {
                        throw new IllegalStateException("Unable to read " + path, e);
                    }
                })
                .map(Path::toString)
                .toList();
        String publisher = Files.readString(Path.of("scripts/spigot-publish.mjs"));

        assertTrue(staleVersions.isEmpty(), () -> "Active release tools contain stale 1.1.0: " + staleVersions);
        assertTrue(publisher.contains("#ctrl_title"),
                "Spigot publisher must fill the required update title field");
    }

    @Test
    void privacyCopyDistinguishesModerationContentFromAnonymousMetrics() throws IOException {
        List<Path> privacySurfaces = List.of(
                Path.of("README.md"),
                Path.of("docs/PRIVACY.md"),
                Path.of("src/main/resources/locale/en_US.yml"),
                Path.of("src/main/resources/locale/es_ES.yml")
        );
        List<String> misleadingClaims = List.of(
                "nothing leaves your server",
                "nothing ever leaves your server",
                "nothing is ever sent off this server",
                "nada sale de este servidor"
        );
        List<String> violations = privacySurfaces.stream()
                .flatMap(path -> {
                    try {
                        String text = Files.readString(path).toLowerCase();
                        return misleadingClaims.stream()
                                .filter(text::contains)
                                .map(claim -> path + ": " + claim);
                    } catch (IOException e) {
                        throw new IllegalStateException("Unable to read " + path, e);
                    }
                })
                .toList();
        String privacyDocument = Files.readString(Path.of("docs/PRIVACY.md"));

        assertTrue(violations.isEmpty(),
                () -> "Privacy copy makes absolute network claims despite bStats:\n" + String.join("\n", violations));
        assertTrue(privacyDocument.contains("bStats") && privacyDocument.contains("chat content"),
                "Privacy documentation must disclose bStats and distinguish it from moderation content");
    }

    @Test
    void currentMarketplaceCopyIsMonitorFirstAndProvidesAnExactActivationTest() throws IOException {
        List<Path> listingSurfaces = List.of(
                Path.of("README.md"),
                Path.of("docs/modrinth-body.md"),
                Path.of("docs/releases/1.4.1.md"),
                Path.of("docs/releases/1.4.1-spigot.bbcode")
        );
        List<String> missingGuidance = listingSurfaces.stream()
                .filter(path -> {
                    try {
                        String text = Files.readString(path).toLowerCase();
                        return !text.contains("/nmod test badword")
                                || !text.contains("monitor")
                                || !text.contains("/nmod mode enforce");
                    } catch (IOException e) {
                        throw new IllegalStateException("Unable to read " + path, e);
                    }
                })
                .map(Path::toString)
                .toList();
        String modrinthBody = Files.readString(Path.of("docs/modrinth-body.md")).toLowerCase();
        String modrinthPublisher = Files.readString(Path.of("scripts/modrinth-publish.mjs")).toLowerCase();
        String hangarPublisher = Files.readString(Path.of("scripts/hangar-publish.mjs")).toLowerCase();
        String pluginYml = Files.readString(Path.of("src/main/resources/plugin.yml")).toLowerCase();

        assertTrue(missingGuidance.isEmpty(),
                () -> "Current listings must explain the safe first test and explicit enforcement: "
                        + missingGuidance);
        assertTrue(!modrinthBody.contains("blocks swearing")
                        && !modrinthBody.contains("detects swearing")
                        && !modrinthBody.contains("works the instant you install")
                        && modrinthBody.contains("safe setup examples")
                        && modrinthBody.contains("1.18.2 through the 1.21.x")
                        && !modrinthBody.contains("folia")
                        && modrinthPublisher.contains("monitor-first")
                        && !modrinthPublisher.contains("\"folia\"")
                        && pluginYml.contains("api-version: 1.18")
                        && hangarPublisher.contains("monitor-first"),
                "Marketplace copy must not claim a fresh monitor-mode install blocks immediately");
    }

    @Test
    void currentReleaseUsesAttributedCloudRecoveryWithoutPromisingUnverifiedActivation() throws IOException {
        String readme = Files.readString(Path.of("README.md"));
        String config = Files.readString(Path.of("src/main/resources/config.yml"));
        String english = Files.readString(Path.of("src/main/resources/locale/en_US.yml"));
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<version>1.4.1</version>")
                        && readme.contains("NeoModeration-1.4.1.jar"),
                "Current build and install documentation must agree on 1.4.1");
        assertTrue(readme.contains("signup?src=neomoderation")
                        && config.contains("signup?src=neomoderation")
                        && readme.contains("billing?src=neomoderation_credits")
                        && config.contains("billing?src=neomoderation_credits"),
                "Acquisition and exhausted-credit recovery links must be attributable");
        assertTrue(!english.contains("Cloud moderation is now &aactive")
                        && english.contains("/nmod doctor"),
                "Saving an unverified key must not claim that cloud moderation is already active");
    }

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

    @Test
    void releaseRunbookCommandsGateSecretsAuditAllChannelsAndUseExistingAssets() throws IOException {
        String text = Files.readString(Path.of("docs/RELEASING.md"));

        assertTrue(text.contains("test -n \"${MODRINTH_TOKEN:-}\" &&\n"
                        + "  node scripts/modrinth-publish.mjs publish \"$JAR\" \"$VERSION\"")
                        && text.contains("If the token check fails, stop"),
                "Modrinth publish must be gated by a non-empty environment token");
        assertTrue(text.contains("bash <<'RELEASE_PREPARE'\nset -euo pipefail"),
                "Multi-command Bash blocks must fail fast and propagate pipeline failures");

        int auditStart = text.indexOf("bash <<'RELEASE_AUDIT'");
        int auditEnd = text.indexOf("RELEASE_AUDIT\n```", auditStart + 1);
        String audit = auditStart >= 0 && auditEnd > auditStart ? text.substring(auditStart, auditEnd) : "";
        int auditExit = audit.indexOf("exit \"$audit_failed\"");
        assertTrue(audit.contains("audit_failed=0")
                        && audit.contains("http_code=")
                        && countOccurrences(audit, "audit_failed=1") >= 4
                        && auditExit > audit.indexOf("api.github.com/repos/KyTDK/NeoModeration/releases")
                        && auditExit > audit.indexOf("api.modrinth.com/v2/project/neomoderation")
                        && auditExit > audit.indexOf("hangar.papermc.io/api/v1/projects/KyTDK/NeoModeration")
                        && auditExit > audit.indexOf("bstats.org/plugin/bukkit/NeoModeration/32542")
                        && !audit.substring(0, auditExit).contains("exit "),
                "Public audit must accumulate every channel failure before returning nonzero");

        Path banner = Path.of("media/banner.png");
        assertTrue(Files.isRegularFile(banner) && text.contains("SPIGOT_BANNER=\"media/banner.png\""),
                "Spigot metadata example must use the existing banner asset");
    }

    @Test
    void releaseRunbookMavenBlocksPinJdk21Locally() throws IOException {
        String text = Files.readString(Path.of("docs/RELEASING.md"));
        Matcher heredocs = Pattern.compile("(?ms)^bash <<'([A-Z_]+)'\\R(.*?)^\\1$").matcher(text);
        List<String> missingJdk = new ArrayList<>();
        int mavenBlocks = 0;
        while (heredocs.find()) {
            String label = heredocs.group(1);
            String block = heredocs.group(2);
            int maven = block.indexOf("mvn ");
            if (maven < 0) {
                continue;
            }
            mavenBlocks++;
            String preamble = block.substring(0, maven);
            if (!preamble.contains("export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home")
                    || !preamble.contains("export PATH=\"$JAVA_HOME/bin:$PATH\"")) {
                missingJdk.add(label);
            }
        }
        assertTrue(mavenBlocks >= 8 && missingJdk.isEmpty(),
                () -> "Every Maven-using release block must pin JDK 21 locally; missing: " + missingJdk);
    }

    @Test
    void releaseAuditVerifiesExpectedPublicArtifacts() throws IOException {
        String text = Files.readString(Path.of("docs/RELEASING.md"));
        int auditStart = text.indexOf("bash <<'RELEASE_AUDIT'");
        int auditEnd = text.indexOf("RELEASE_AUDIT\n```", auditStart + 1);
        String audit = auditStart >= 0 && auditEnd > auditStart ? text.substring(auditStart, auditEnd) : "";

        assertTrue(audit.contains("github_url=\"https://api.github.com/repos/KyTDK/NeoModeration/releases/tags/v${VERSION}\"")
                        && audit.contains("github_expected_tag=\"v${VERSION}\"")
                        && audit.contains("github_expected_asset=\"NeoModeration-${VERSION}.jar\"")
                        && audit.contains(".tag_name == $tag")
                        && audit.contains("any(.assets[]?; .name == $asset)"),
                "GitHub audit must match the requested tag and JAR asset");
        assertTrue(audit.contains("modrinth_versions_url='https://api.modrinth.com/v2/project/neomoderation/version'")
                        && audit.contains("any(.[]?; .version_number == $version)"),
                "Modrinth audit must match the expected public version number");
        assertTrue(audit.contains(".visibility == \"public\"")
                        && audit.contains("hangar_versions_url='https://hangar.papermc.io/api/v1/projects/KyTDK/NeoModeration/versions'")
                        && audit.contains("any(.result[]?; .name == $version and .visibility == \"public\")"),
                "Hangar audit must match public project visibility and the expected public version");
        assertTrue(audit.indexOf("bstats.org/plugin/bukkit/NeoModeration/32542")
                        > audit.indexOf("hangar_versions_url=")
                        && audit.indexOf("exit \"$audit_failed\"")
                        > audit.indexOf("bstats.org/plugin/bukkit/NeoModeration/32542"),
                "bStats must be checked after marketplace predicates and before the audit returns");
    }

    private static Stream<String> scan(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            return Stream.iterate(0, index -> index + 1)
                    .limit(lines.size())
                    .filter(index -> lines.get(index).contains(".printStackTrace()")
                            || lines.get(index).contains("throw new RuntimeException("))
                    .map(index -> path + ":" + (index + 1) + " " + lines.get(index).trim());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to scan " + path, e);
        }
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int index = 0; (index = text.indexOf(needle, index)) >= 0; index += needle.length()) {
            count++;
        }
        return count;
    }

    private static NodeResult runNodeAssertions(String source) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("node", "--input-type=module", "--eval", source);
        builder.directory(Path.of(".").toFile());
        builder.redirectErrorStream(true);
        builder.environment().remove("MODRINTH_TOKEN");
        builder.environment().remove("HANGAR_API_KEY");
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());
        return new NodeResult(process.waitFor(), output);
    }

    private record NodeResult(int exitCode, String output) {
    }
}
