package io.github.silkypanda.blockvault;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import io.github.silkypanda.blockvault.core.session.Scheduler;

/** Separate lazy daemons for vault I/O and timers; scheduled callbacks return to the game thread. */
public final class BackgroundExecutor implements Scheduler {
    private static final BackgroundExecutor SCHEDULER = new BackgroundExecutor();
    private static java.util.concurrent.ExecutorService executor;
    private static ScheduledExecutorService scheduler;

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
        ScheduledFuture<?> future = timerExecutor().schedule(() -> onGameThread(callback), delayMillis, TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    @Override
    public long nowMillis() { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()); }

    private static synchronized java.util.concurrent.ExecutorService executor() {
        if (executor == null) executor = Executors.newSingleThreadExecutor(threadFactory("blockvault-io"));
        return executor;
    }

    private static synchronized ScheduledExecutorService timerExecutor() {
        if (scheduler == null) {
            ScheduledThreadPoolExecutor timers = new ScheduledThreadPoolExecutor(1, threadFactory("blockvault-timer"));
            timers.setRemoveOnCancelPolicy(true);
            scheduler = timers;
        }
        return scheduler;
    }

    private static ThreadFactory threadFactory(String name) {
        return task -> {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
