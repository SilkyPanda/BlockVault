package io.github.silkypanda.blockvault;

import net.minecraft.client.gui.screens.Screen;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Loads the optional YACL screen reflectively so YACL classes are absent when that dependency is not installed. */
final class VaultSettingsScreenFactory {
    private static final Set<Screen> YACL_SCREENS = Collections.newSetFromMap(new WeakHashMap<>());

    private VaultSettingsScreenFactory() { }

    /** Creates a settings screen and records optional YACL screens. Call on the game thread. */
    static Screen create(Screen parent) {
        try {
            Class<?> yaclScreen = Class.forName("io.github.silkypanda.blockvault.VaultYaclSettingsScreen");
            Screen screen = (Screen) yaclScreen.getDeclaredConstructor(Screen.class).newInstance(parent);
            YACL_SCREENS.add(screen);
            return screen;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return new VaultNativeSettingsScreen(parent);
        }
    }

    /** The generated screen belongs to YACL and cannot implement BlockVault's marker interface. */
    static boolean isOptionalYaclScreen(Screen screen) { return YACL_SCREENS.contains(screen); }
}
