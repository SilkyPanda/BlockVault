package io.github.silkypanda.blockvault;

import java.nio.file.Path;

/** Loader services required by shared client code. */
public interface Platform {
    /** Returns the loader identifier, either {@code fabric} or {@code neoforge}. */
    String loaderName();

    /** Returns the Minecraft game directory. */
    Path gameDir();

    /** Returns whether the loader is running a development environment. */
    boolean isDevEnvironment();
}
