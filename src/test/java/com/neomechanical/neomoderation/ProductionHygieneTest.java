package com.neomechanical.neomoderation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
}
