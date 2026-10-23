package io.github.silkypanda.blockvault;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import io.github.silkypanda.blockvault.core.session.Scheduler;

/** Single lazy daemon for vault work and timers; scheduled callbacks are returned to the game thread. */
public final class BackgroundExecutor implements Scheduler {
    private static final BackgroundExecutor SCHEDULER = new BackgroundExecutor();
    private static ScheduledExecutorService executor;

    private BackgroundExecutor() { }

    /** Returns the shared session scheduler. */
    public static BackgroundExecutor scheduler() { return SCHEDULER; }

    /** Runs a task on the single background thread and completes the future with its result. */
    public static <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor().execute(() -> {
            try { future.complete(task.call()); }
            catch (Throwable failure) { future.completeExceptionally(failure); }
        });
        return future;
    }

    /** Runs a callback on the Minecraft game thread. */
    public static void onGameThread(Runnable callback) {
        Minecraft.getInstance().execute(callback);
    }

    @Override
    public Handle schedule(long delayMillis, Runnable callback) {
        ScheduledFuture<?> future = executor().schedule(() -> onGameThread(callback), delayMillis, TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    @Override
    public long nowMillis() { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()); }

    private static synchronized ScheduledExecutorService executor() {
        if (executor == null) {
            ThreadFactory factory = task -> {
                Thread thread = new Thread(task, "blockvault-io");
                thread.setDaemon(true);
                return thread;
            };
            executor = Executors.newSingleThreadScheduledExecutor(factory);
        }
        return executor;
    }
}
