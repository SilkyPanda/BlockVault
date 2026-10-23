package io.github.silkypanda.blockvault.core.session;

/** Timer abstraction so session timing can be tested without Minecraft. */
public interface Scheduler {
    /** A scheduled callback that can be cancelled before it runs. */
    interface Handle {
        /** Prevents this callback from running if it has not started. */
        void cancel();
    }

    /** Schedules a callback after a delay in milliseconds. */
    Handle schedule(long delayMillis, Runnable callback);
    /** Returns the current monotonic time in milliseconds. */
    long nowMillis();
}
