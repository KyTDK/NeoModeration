package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.ModerationSettings;
import org.bukkit.entity.Player;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public final class ChatModerationCoordinator implements AutoCloseable {
    private static final int POOL_SIZE = 2;
    private static final long MAX_WAIT_MS = 2500L;

    private final AtomicInteger workerSeq = new AtomicInteger();
    private final ModerationCircuitBreaker circuit;
    private final ModerationApiClient apiClient;
    private final ExecutorService workers;

    public ChatModerationCoordinator(Logger logger) {
        this(new ModerationCircuitBreaker(logger), new ModerationApiClient());
    }

    ChatModerationCoordinator(ModerationCircuitBreaker circuit, ModerationApiClient apiClient) {
        this.circuit = circuit;
        this.apiClient = apiClient;
        this.workers = Executors.newFixedThreadPool(POOL_SIZE, task -> {
            Thread thread = new Thread(task, "NeoModeration-worker-" + workerSeq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    public void resetCircuit() {
        circuit.reset();
    }

    public ModerationApiClient apiClient() {
        return apiClient;
    }

    public boolean isRemoteCallAllowed() {
        return circuit.isRemoteCallAllowed();
    }

    @Override
    public void close() {
        workers.shutdownNow();
    }

    public boolean isMessageFlagged(Player player, String message, ModerationSettings settings) {
        if (!circuit.isRemoteCallAllowed()) {
            return !settings.failOpen();
        }

        long waitMs = Math.min(
                MAX_WAIT_MS,
                (long) settings.api().connectTimeoutMs() + settings.api().readTimeoutMs() + 400L
        );
        Future<ModerationApiResult> future = workers.submit(() -> apiClient.moderateText(
                player.getName(),
                player.getUniqueId().toString(),
                message,
                settings.api(),
                settings.categories()
        ));

        try {
            ModerationApiResult result = future.get(Math.max(1L, waitMs), TimeUnit.MILLISECONDS);
            circuit.record(result);
            return result.isFlagged()
                    || (result.kind() != ModerationApiResult.Kind.CLEAR && !settings.failOpen());
        } catch (TimeoutException e) {
            future.cancel(true);
            circuit.record(ModerationApiResult.transientTransport());
            return !settings.failOpen();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            circuit.record(ModerationApiResult.transientTransport());
            return !settings.failOpen();
        } catch (ExecutionException e) {
            circuit.record(ModerationApiResult.transientTransport());
            return !settings.failOpen();
        }
    }
}
