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
    private final ChatModerationApiClient apiClient;
    private final ExecutorService workers;

    public ChatModerationCoordinator(Logger logger) {
        this(new ModerationCircuitBreaker(logger), new ChatModerationApiClient());
    }

    ChatModerationCoordinator(ModerationCircuitBreaker circuit, ChatModerationApiClient apiClient) {
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

    public boolean isRemoteCallAllowed() {
        return circuit.isRemoteCallAllowed();
    }

    @Override
    public void close() {
        workers.shutdownNow();
    }

    public boolean isMessageFlagged(Player player, String message, ModerationSettings settings) {
        if (!circuit.isRemoteCallAllowed()) {
            return false;
        }

        long waitMs = Math.min(
                MAX_WAIT_MS,
                (long) settings.api().connectTimeoutMs() + settings.api().readTimeoutMs() + 400L
        );
        Future<ModerationApiResult> future = workers.submit(() -> apiClient.moderate(
                player.getName(),
                player.getUniqueId().toString(),
                message,
                settings.api(),
                settings.categories()
        ));

        try {
            ModerationApiResult result = future.get(Math.max(1L, waitMs), TimeUnit.MILLISECONDS);
            circuit.record(result);
            return result.isFlagged();
        } catch (TimeoutException e) {
            future.cancel(true);
            circuit.record(ModerationApiResult.transientTransport());
            return false;
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            circuit.record(ModerationApiResult.transientTransport());
            return false;
        } catch (ExecutionException e) {
            circuit.record(ModerationApiResult.transientTransport());
            return false;
        }
    }
}
