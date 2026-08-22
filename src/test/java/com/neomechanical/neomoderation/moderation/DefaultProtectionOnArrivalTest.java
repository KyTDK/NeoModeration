package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.OfflineModerationSettings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fresh install must detect something real. The shipped default list used to be
 * the placeholders "badword" and "scam", so a new admin's first test of actual
 * chat abuse came back clean and was indistinguishable from a broken install.
 */
class DefaultProtectionOnArrivalTest {

    /** Reads the bannedWords list exactly as a fresh install would receive it. */
    private static List<String> shippedBannedWords() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("src/main/resources/config.yml"));
        List<String> words = new ArrayList<>();
        boolean inList = false;
        for (String line : lines) {
            if (line.strip().equals("bannedWords:")) {
                inList = true;
                continue;
            }
            if (!inList) {
                continue;
            }
            String stripped = line.strip();
            if (!stripped.startsWith("- ")) {
                break;
            }
            words.add(stripped.substring(2).trim().replaceAll("^\"|\"$", ""));
        }
        return words;
    }

    private static OfflineModerationSettings freshInstall() throws IOException {
        return new OfflineModerationSettings(
                true, false, true, shippedBannedWords(), List.of(), List.of(), List.of());
    }

    @Test
    void freshInstallFlagsRealChatAbuseNotOnlyThePlaceholderWord() throws IOException {
        OfflineModerationSettings s = freshInstall();

        for (String message : List.of("fuck you noob", "you are a bitch", "shit server")) {
            assertTrue(OfflineModerationEngine.evaluate(message, s).flagged(),
                    () -> "a fresh install must flag real abuse, but allowed: " + message);
        }
    }

    @Test
    void freshInstallKeepsTheDocumentedSafeTestWorking() throws IOException {
        OfflineModerationSettings s = freshInstall();

        assertTrue(OfflineModerationEngine.evaluate("badword", s).flagged(),
                "listings and /nmod help document `/nmod test badword` as the safe first check");
    }

    @Test
    void freshInstallDoesNotFlagInnocentWordsContainingProfaneSubstrings() throws IOException {
        OfflineModerationSettings s = freshInstall();

        for (String message : List.of(
                "the assignment is due",
                "run the analysis again",
                "walking on the grass",
                "I live in Scunthorpe",
                "pass me a cocktail",
                "classic build")) {
            assertFalse(OfflineModerationEngine.evaluate(message, s).flagged(),
                    () -> "a fresh install must not flag innocent chat, but flagged: " + message);
        }
    }
}
