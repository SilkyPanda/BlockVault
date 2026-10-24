package io.github.silkypanda.blockvault;

/** Loader services required by shared client code. */
public interface Platform {
    /** Returns the loader identifier, either {@code fabric} or {@code neoforge}. */
    String loaderName();
}
