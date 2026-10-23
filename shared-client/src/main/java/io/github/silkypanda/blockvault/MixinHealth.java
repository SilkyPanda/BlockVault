package io.github.silkypanda.blockvault;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tracks whether critical command interception hooks have run at least once. */
public final class MixinHealth {
    private static final Logger LOGGER = LoggerFactory.getLogger("BlockVault");
    private static final AtomicBoolean PACKET = new AtomicBoolean();
    private static final AtomicBoolean COMMAND = new AtomicBoolean();
    private static final AtomicBoolean HISTORY = new AtomicBoolean();
    private static final AtomicBoolean CHECKED = new AtomicBoolean();

    private MixinHealth() { }

    public static void packetRan() { PACKET.set(true); }
    public static void commandRan() { COMMAND.set(true); }
    public static void historyRan() { HISTORY.set(true); }

    public static void checkAfterJoin() {
        if (!CHECKED.compareAndSet(false, true)) return;
        BackgroundExecutor.scheduler().schedule(5000, () -> {
            if (!PACKET.get()) LOGGER.warn("BlockVault commands-packet hook did not run");
            if (!COMMAND.get()) LOGGER.warn("BlockVault command-send hook did not run");
            if (!HISTORY.get()) LOGGER.warn("BlockVault command-history hook did not run");
        });
    }
}
