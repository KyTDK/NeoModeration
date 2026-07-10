package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.OfflineModerationSettings;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Throughput guard for the hot chat path using a real 400+ entry profanity list
 * (LDNOOBW). Not a micro-benchmark framework, but strict enough to catch a
 * per-message pattern-compilation regression by an order of magnitude.
 */
class OfflineModerationEngineBenchmarkTest {
    private static final int MESSAGES = 5_000;

    @Test
    void sustainsChatFloodAgainstRealWordList() throws Exception {
        List<String> words = loadWords();
        assertTrue(words.size() > 300, "expected the real word list, got " + words.size());

        OfflineModerationSettings settings = new OfflineModerationSettings(
                true, false, true, words, List.of("grabify.link", "discord.gg/free"));

        List<String> corpus = corpus(words);

        // Warm-up pass (JIT + any caches).
        for (String message : corpus) {
            OfflineModerationEngine.evaluate(message, settings);
        }

        long start = System.nanoTime();
        int flagged = 0;
        for (String message : corpus) {
            if (OfflineModerationEngine.evaluate(message, settings).flagged()) {
                flagged++;
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        double perMessageMicros = (System.nanoTime() - start) / 1_000.0 / MESSAGES;

        System.out.printf("offline-engine: %d msgs, %d flagged, %d ms total, %.1f us/msg%n",
                MESSAGES, flagged, elapsedMs, perMessageMicros);

        assertTrue(flagged > 0, "corpus should contain flaggable messages");
        // Real servers see bursts of ~50 msgs/s; anything above 2ms/msg average
        // for a plain word check would burn a full core just on chat.
        assertTrue(perMessageMicros < 2_000,
                "offline engine too slow: " + perMessageMicros + " us/msg");
    }

    private static List<String> loadWords() throws Exception {
        try (InputStream stream = OfflineModerationEngineBenchmarkTest.class
                .getResourceAsStream("/badwords-en.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            List<String> words = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    words.add(line.trim());
                }
            }
            return words;
        }
    }

    /** Realistic chat mix: mostly clean lines, ~4% containing a banned word. */
    private static List<String> corpus(List<String> words) {
        String[] clean = {
                "anyone selling diamonds at spawn",
                "lol that creeper got me again",
                "tp me pls",
                "who griefed my base???",
                "gg wp everyone",
                "meet at 120 64 -340",
                "can someone craft me an elytra",
                "brb dinner",
                "the nether hub needs more ice",
                "trading 3 stacks of iron for emeralds"
        };
        Random random = new Random(42);
        List<String> corpus = new ArrayList<>(MESSAGES);
        for (int i = 0; i < MESSAGES; i++) {
            if (random.nextInt(25) == 0) {
                String word = words.get(random.nextInt(words.size()));
                corpus.add("you are such a " + word + " honestly");
            } else {
                corpus.add(clean[random.nextInt(clean.length)] + " #" + i);
            }
        }
        return corpus;
    }
}
