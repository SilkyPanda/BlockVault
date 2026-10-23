package io.github.silkypanda.blockvault;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared client bootstrap. Initialization stores loader services and performs no I/O. */
public final class BlockVault {
    private static final Logger LOGGER = LoggerFactory.getLogger("BlockVault");
    private static Platform platform;

    private BlockVault() {
    }

    /** Stores the loader platform and reports initialization once. */
    public static void init(Platform value) {
        platform = value;
        LOGGER.info("BlockVault initialized ({})", value.loaderName());
    }

    /** Returns the platform supplied by the loader entrypoint. */
    public static Platform platform() {
        if (platform == null) {
            throw new IllegalStateException("BlockVault has not been initialized");
        }
        return platform;
    }
}
