package io.github.silkypanda.blockvault;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared client bootstrap. Initialization stores loader services and performs no I/O. */
public final class BlockVault {
    private static final Logger LOGGER = LoggerFactory.getLogger("BlockVault");
    private BlockVault() {
    }

    /** Reports which loader initialized the shared client code. */
    public static void init(Platform value) {
        LOGGER.info("BlockVault initialized ({})", value.loaderName());
    }
}
