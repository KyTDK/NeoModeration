package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.OfflineModerationSettings;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bypass benchmark for the offline engine.
 *
 * <p>Every install uses the offline engine; almost none attach the cloud. So the
 * offline engine is the product for practical purposes, and a word filter that a
 * player defeats by typing {@code f uck} is not one. This measures the two
 * numbers that matter together — how many obfuscations are caught, and how many
 * innocent lines are wrongly caught — because either alone is easy to game.</p>
 *
 * <p>Baseline when this was written (1.5.0): <b>10 of 51</b> obfuscations
 * detected, 0 false positives.</p>
 */
class EvasionResistanceTest {

    private record Case(boolean shouldFlag, String message) {
    }

    private static final List<String> BANNED = List.of(
            "badword", "fuck", "shit", "bitch", "ass", "kys", "nigger");

    @Test
    void catchesObfuscationWithoutFlaggingInnocentChat() throws Exception {
        List<Case> cases = load();
        OfflineModerationSettings settings = new OfflineModerationSettings(
                true, false, true, BANNED, List.of(), List.of(), List.of());

        List<String> missed = new ArrayList<>();
        List<String> falsePositives = new ArrayList<>();
        int positives = 0;
        int negatives = 0;

        for (Case testCase : cases) {
            boolean flagged = OfflineModerationEngine.evaluate(testCase.message(), settings).flagged();
            if (testCase.shouldFlag()) {
                positives++;
                if (!flagged) {
                    missed.add(testCase.message());
                }
            } else {
                negatives++;
                if (flagged) {
                    falsePositives.add(testCase.message());
                }
            }
        }

        int caught = positives - missed.size();
        System.out.printf("evasion: caught %d/%d (%.0f%%), false positives %d/%d%n",
                caught, positives, 100.0 * caught / positives, falsePositives.size(), negatives);
        missed.forEach(m -> System.out.println("  MISSED: " + m));
        falsePositives.forEach(f -> System.out.println("  FALSE POSITIVE: " + f));

        // A false positive is worse than a miss: it punishes an innocent player,
        // which is how a moderation plugin gets uninstalled. Zero tolerance.
        assertTrue(falsePositives.isEmpty(),
                "offline engine flagged innocent chat: " + falsePositives);
        assertTrue(caught >= (int) Math.ceil(positives * 0.90),
                "evasion resistance regressed: caught " + caught + "/" + positives
                        + ", missed " + missed);
    }

    private static List<Case> load() throws Exception {
        try (InputStream stream = EvasionResistanceTest.class.getResourceAsStream("/evasion-cases.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            List<Case> cases = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                int tab = line.indexOf('\t');
                assertTrue(tab > 0, "malformed case line: " + line);
                String expectation = line.substring(0, tab).trim();
                String message = line.substring(tab + 1);
                cases.add(new Case("FLAG".equals(expectation), message));
            }
            return cases;
        }
    }
}
