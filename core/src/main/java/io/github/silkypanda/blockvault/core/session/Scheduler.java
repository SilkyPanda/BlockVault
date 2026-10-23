package io.github.silkypanda.blockvault.core.session;

/** Timer abstraction so session timing can be tested without Minecraft. */
public interface Scheduler {
    /** Schedules a callback after a delay in milliseconds. */
    void schedule(long delayMillis, Runnable callback);
    /** Returns the current monotonic time in milliseconds. */
    long nowMillis();
}
