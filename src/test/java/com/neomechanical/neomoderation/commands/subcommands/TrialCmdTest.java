package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.config.ModerationApiSettings;
import com.neomechanical.neomoderation.config.ModerationSettings;
import com.neomechanical.neomoderation.messages.MessageService;
import com.neomechanical.neomoderation.moderation.TrialClient;
import com.neomechanical.neomoderation.platform.PlatformScheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TrialCmdTest {
    @TempDir
    Path tempDir;

    private NeoModerationPlugin plugin;
    private MessageService messages;
    private ModerationSettings settings;
    private ModerationApiSettings apiSettings;
    private PlatformScheduler scheduler;
    private YamlConfiguration config;
    private CommandSender sender;
    private TrialClient trialClient;

    @BeforeEach
    void setUp() {
        plugin = mock(NeoModerationPlugin.class);
        messages = mock(MessageService.class);
        settings = mock(ModerationSettings.class);
        apiSettings = mock(ModerationApiSettings.class);
        scheduler = mock(PlatformScheduler.class);
        config = new YamlConfiguration();
        sender = mock(CommandSender.class);
        trialClient = mock(TrialClient.class);

        when(plugin.messages()).thenReturn(messages);
        when(plugin.settings()).thenReturn(settings);
        when(plugin.scheduler()).thenReturn(scheduler);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(settings.api()).thenReturn(apiSettings);

        // Run async scheduler callbacks synchronously in tests
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(scheduler).runAsync(any(Runnable.class));
    }

    @Test
    void rejectsIfApiKeyAlreadyConfigured() {
        when(apiSettings.apiKey()).thenReturn("existing_key_123");

        TrialCmd cmd = new TrialCmd(plugin, trialClient);
        cmd.execute(sender, "nmod", new String[]{"trial"});

        verify(messages).send(eq(sender), eq("trial.already-configured"));
        verifyNoInteractions(trialClient);
    }

    @Test
    void successfulActivationSavesKeyAndEnablesModeration() throws Exception {
        when(apiSettings.apiKey()).thenReturn("");
        when(trialClient.activateTrial(any(), any(), any()))
                .thenReturn(new TrialClient.TrialResult("nmt_trial_key_999", "2026-10-15T00:00:00Z", 14));

        TrialCmd cmd = new TrialCmd(plugin, trialClient);
        cmd.execute(sender, "nmod", new String[]{"trial"});

        verify(messages).send(eq(sender), eq("trial.requesting"));
        verify(plugin).saveAndReload();
        assertEquals(true, config.get("moderation.enabled"));
        assertEquals("nmt_trial_key_999", config.get("moderation.api.apiKey"));
        verify(messages).send(eq(sender), eq("trial.activated"), argThat(map ->
                "14".equals(map.get("days")) && "2026-10-15T00:00:00Z".equals(map.get("expires"))
        ));
    }

    @Test
    void alreadyClaimedShowsBillingUrl() throws Exception {
        when(apiSettings.apiKey()).thenReturn("");
        when(trialClient.activateTrial(any(), any(), any()))
                .thenThrow(new TrialClient.TrialException(TrialClient.TrialError.ALREADY_CLAIMED, "claimed"));

        TrialCmd cmd = new TrialCmd(plugin, trialClient);
        cmd.execute(sender, "nmod", new String[]{"trial"});

        verify(messages).send(eq(sender), eq("trial.already-claimed"), argThat(map ->
                map.containsKey("url") && map.get("url").contains("neomechanical.com/billing")
        ));
    }

    @Test
    void rateLimitedShowsRateLimitMessage() throws Exception {
        when(apiSettings.apiKey()).thenReturn("");
        when(trialClient.activateTrial(any(), any(), any()))
                .thenThrow(new TrialClient.TrialException(TrialClient.TrialError.RATE_LIMITED, "rate limit"));

        TrialCmd cmd = new TrialCmd(plugin, trialClient);
        cmd.execute(sender, "nmod", new String[]{"trial"});

        verify(messages).send(eq(sender), eq("trial.rate-limited"));
    }

    @Test
    void hmacGenerationIsDeterministicAndNonEmpty() {
        String hmac1 = TrialClient.computeHmac("11111111-2222-3333-4444-555555555555", 1700000000L);
        String hmac2 = TrialClient.computeHmac("11111111-2222-3333-4444-555555555555", 1700000000L);
        String hmacDiff = TrialClient.computeHmac("11111111-2222-3333-4444-555555555555", 1700000001L);

        assertEquals(64, hmac1.length());
        assertEquals(hmac1, hmac2);
        org.junit.jupiter.api.Assertions.assertNotEquals(hmac1, hmacDiff);
    }

    @Test
    void statusSubcommandReportsActiveTrial() throws Exception {
        when(apiSettings.apiKey()).thenReturn("nmt_trial_key_999");
        when(trialClient.fetchTrialStatus(apiSettings)).thenReturn(
                new TrialClient.TrialStatusResult(
                        true,
                        "active",
                        "2026-10-22T00:00:00Z",
                        12,
                        "https://neomechanical.com/billing?src=neomoderation_trial_expired"
                )
        );

        TrialCmd cmd = new TrialCmd(plugin, trialClient);
        cmd.execute(sender, "nmod", new String[]{"trial", "status"});

        verify(messages).send(eq(sender), eq("trial.status-checking"));
        verify(messages).send(eq(sender), eq("trial.status-active"), argThat(map ->
                "12".equals(map.get("days"))
                        && "2026-10-22T00:00:00Z".equals(map.get("expires"))
                        && map.get("url").contains("neomechanical.com/billing")
        ));
    }

    @Test
    void statusSubcommandReportsExpiredTrial() throws Exception {
        when(apiSettings.apiKey()).thenReturn("nmt_trial_key_999");
        when(trialClient.fetchTrialStatus(apiSettings)).thenReturn(
                new TrialClient.TrialStatusResult(
                        true,
                        "expired",
                        "2026-10-01T00:00:00Z",
                        0,
                        "https://neomechanical.com/billing?src=neomoderation_trial_expired"
                )
        );

        TrialCmd cmd = new TrialCmd(plugin, trialClient);
        cmd.execute(sender, "nmod", new String[]{"trial", "status"});

        verify(messages).send(eq(sender), eq("trial.status-expired"), argThat(map ->
                "2026-10-01T00:00:00Z".equals(map.get("expires"))
                        && map.get("url").contains("neomechanical.com/billing")
        ));
    }

    @Test
    void statusSubcommandPromptsForTrialWhenNoKeyConfigured() {
        when(apiSettings.apiKey()).thenReturn("");

        TrialCmd cmd = new TrialCmd(plugin, trialClient);
        cmd.execute(sender, "nmod", new String[]{"trial", "status"});

        verify(messages).send(eq(sender), eq("trial.status-no-key"), argThat(map ->
                map.containsKey("url") && map.get("url").contains("neomechanical.com/signup")
        ));
        verifyNoInteractions(trialClient);
    }
}
