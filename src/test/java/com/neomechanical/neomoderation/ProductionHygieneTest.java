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
                        && modrinth.contains("commands: open | check | inspect | patdebug | patdom | publish <jar> <version>"),
                "Modrinth usage and help must document the dispatched environment-token interface");
        List<String> modrinthCommands = List.of("open", "check", "inspect", "patdebug", "patdom", "publish");
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

        assertTrue(audit.contains("github_url='https://api.github.com/repos/KyTDK/NeoModeration/releases/latest'")
                        && audit.contains("github_expected_tag=\"v${VERSION}\"")
                        && audit.contains("github_expected_asset=\"NeoModeration-${VERSION}.jar\"")
                        && audit.contains(".tag_name == $tag")
                        && audit.contains("any(.assets[]?; .name == $asset)"),
                "GitHub audit must match the expected latest tag and JAR asset");
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
}
