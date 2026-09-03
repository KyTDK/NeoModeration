package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.ModerationMode;
import com.neomechanical.neomoderation.config.ModerationSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * What the console says when the plugin comes up.
 *
 * From 1.6.0 a new install enforces the local word, URL and spam rules straight
 * away, and only monitors cloud decisions. Before that it monitored everything,
 * so a fresh install blocked nothing at all and looked identical to a plugin
 * that simply was not working -- 99 downloads had produced 4 retained servers.
 *
 * <p>Existing installs are never switched to enforce behind the operator's back:
 * their config.yml keeps whatever it already said. Instead, when this build
 * finds itself monitoring everything, it says so and names the one command that
 * changes it.
 *
 * This states plainly what is running, what is not, and the one command that
 * changes it. Kept as a pure function of settings so it is testable without a
 * server, and deliberately English: it is console output for operators, not
 * player-facing text, so it stays out of the locale files.
 */
public final class StartupSummary {

    private StartupSummary() {
    }

    public static List<String> lines(ModerationSettings settings, String version) {
        List<String> lines = new ArrayList<>();
        lines.add("NeoModeration " + version + " enabled.");

        if (!settings.enabled()) {
            lines.add("Moderation is disabled in config.yml - nothing will be checked. "
                    + "Set moderation.enabled to true to switch it on.");
            lines.add("Run /nmod status at any time for the full picture.");
            return lines;
        }

        boolean monitor = settings.mode() == ModerationMode.MONITOR;
        if (monitor) {
            lines.add("Mode: MONITOR - detections are logged and alerted, but nothing is blocked "
                    + "or punished. Run /nmod mode enforce when you are happy with the decisions.");
        } else {
            lines.add("Mode: ENFORCE - flagged content is blocked and the configured actions run.");
        }
        if (!settings.api().apiKey().isBlank()) {
            lines.add(settings.cloudMode() == ModerationMode.MONITOR
                    ? "Cloud mode: MONITOR - cloud decisions are alerted only. "
                            + "Run /nmod cloudmode enforce once you trust them."
                    : "Cloud mode: ENFORCE - cloud decisions block and punish like local ones.");
        }

        lines.add("Active now: " + String.join(", ", activeProtections(settings)) + ".");

        List<String> inactive = inactiveProtections(settings);
        if (!inactive.isEmpty()) {
            lines.add("Not active: " + String.join(", ", inactive) + ".");
        }

        if (monitor) {
            lines.add("Try /nmod test badword now: it previews the bundled local rule, but MONITOR "
                    + "mode never blocks or punishes. Use /nmod mode enforce only when ready.");
        } else {
            lines.add("Verify the bundled local rule with /nmod test badword; tests never execute actions.");
        }

        lines.add("Run /nmod status at any time for the full picture.");
        return lines;
    }

    private static List<String> activeProtections(ModerationSettings settings) {
        List<String> active = new ArrayList<>();
        if (settings.spam().enabled()) {
            active.add("anti-spam (rate, duplicates, caps)");
        }
        if (settings.offline().enabled()) {
            active.add(settings.offline().bannedWords().size() + " word rules and "
                    + settings.offline().bannedUrls().size() + " URL rules");
        }
        if (!settings.api().apiKey().isBlank()) {
            active.add("cloud moderation (" + settings.categories().enabledCount() + " categories)");
        }
        if (active.isEmpty()) {
            active.add("nothing - every check is switched off in config.yml");
        }
        return active;
    }

    private static List<String> inactiveProtections(ModerationSettings settings) {
        List<String> inactive = new ArrayList<>();
        if (settings.api().apiKey().isBlank()) {
            // The single biggest capability gap on a fresh install, and the only one
            // that needs a step outside the server.
            inactive.add("cloud moderation and map-art scanning (no API key - "
                    + "sign up at " + CloudRecovery.SIGNUP_URL
                    + ", create a key, then run /nmod setup <key>)");
        }
        if (!settings.spam().enabled()) {
            inactive.add("anti-spam");
        }
        if (!settings.offline().enabled()) {
            inactive.add("local word and URL rules");
        }
        return inactive;
    }
}
