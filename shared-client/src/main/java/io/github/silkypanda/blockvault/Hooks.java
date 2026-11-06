package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.detect.PatternPack;
import io.github.silkypanda.blockvault.core.gen.PasswordGenerator;
import io.github.silkypanda.blockvault.core.host.HostKey;
import io.github.silkypanda.blockvault.core.session.LoginSession;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bridges verified loader callbacks and Minecraft mixins to the pure-Java session. */
public final class Hooks {
    private static final Logger LOGGER = LoggerFactory.getLogger("BlockVault");
    private static final ThreadLocal<Boolean> SELF_SENT = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> HISTORY_SCRUBBED = ThreadLocal.withInitial(() -> false);
    private static final BackgroundExecutor SCHEDULER = BackgroundExecutor.scheduler();
    private static volatile PatternPack patterns;
    private static volatile Set<String> sensitiveAliases = Set.of("login", "register", "reg", "changepassword", "changepass", "unregister");
    private static LoginSession session;
    private static McSessionEnv environment;
    /** Set by the outgoing-command mixin; used to detect that the mixin is really applied. */
    private static volatile boolean commandHookSeen;
    private static boolean warnedCommandHook;
    private static String sessionHost;
    private static String sessionUser;
    private static int sessionErrors;
    private static boolean warnedHistoryHook;
    private static boolean settingsLoaded;
    private static String queuedPrompt;

    private Hooks() { }

    /** Starts a multiplayer-only session when Minecraft has a current remote server. */
    public static void onJoin() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCurrentServer() == null || minecraft.getUser() == null) {
            onDisconnect();
            return;
        }
        String address = minecraft.getCurrentServer().ip;
        String host = HostKey.normalize(address).orElse(null);
        if (host == null) {
            onDisconnect();
            return;
        }
        VaultRuntime.deliverPendingTrustNotice();
        onDisconnect();
        try {
            patterns = loadPatterns();
            sensitiveAliases = java.util.stream.Stream.of("login", "register", "changepassword", "unregister")
                    .flatMap(kind -> patterns.commandNames(kind).stream()).collect(Collectors.toUnmodifiableSet());
            environment = new McSessionEnv();
            LoginSession active = new LoginSession(environment, SCHEDULER, patterns, PasswordGenerator.Policy.pickyServer());
            session = active;
            sessionHost = host;
            sessionUser = minecraft.getUser().getName();
            sessionErrors = 0;
            environment.attach(active);
            active.onJoin(host, minecraft.getUser().getName());
            BlockVaultConfig.load(settings -> {
                if (session == active) {
                    active.configure(settings.autofill(), settings.passwordlessLogin(), settings.autofillDelayMillis(),
                            BlockVaultConfig.generatorPolicy(settings.generator()));
                    settingsLoaded = true;
                    String queued = queuedPrompt;
                    queuedPrompt = null;
                    if (queued != null) guard(active, () -> active.onSystemMessage(queued));
                }
            });
        } catch (Exception failure) {
            LOGGER.warn("BlockVault could not initialize login detection");
            onDisconnect();
        }
    }

    private static PatternPack loadPatterns() throws java.io.IOException {
        InputStream defaults = Hooks.class.getResourceAsStream("/assets/blockvault/patterns/default.json");
        if (defaults == null) throw new java.io.IOException("Built-in pattern resource is missing");
        return PatternPack.load(defaults, io.github.silkypanda.blockvault.core.vault.DataDirs.resolve()
                .resolve("patterns.json"), warning -> LOGGER.warn("BlockVault skipped an invalid pattern"));
    }

    static boolean isSensitiveAlias(String command) {
        String normalized = command.toLowerCase(java.util.Locale.ROOT);
        if (!sensitiveAliases.contains(normalized)) return false;
        if (normalized.length() > 3) return true;
        LoginSession active = session;
        return active != null && active.wasShortAliasPrompted(normalized);
    }

    /** Ends the active session and wipes its in-memory credentials. */
    public static void onDisconnect() {
        if (session != null) session.onDisconnect();
        if (environment != null) environment.disconnect();
        VaultRuntime.clearWaiting();
        session = null;
        environment = null;
        settingsLoaded = false;
        queuedPrompt = null;
    }

    static void applySettings(BlockVaultConfig.Settings settings) {
        LoginSession active = session;
        if (active != null) active.configure(settings.autofill(), settings.passwordlessLogin(), settings.autofillDelayMillis(),
                BlockVaultConfig.generatorPolicy(settings.generator()));
    }

    /** Delivers plain system chat to the login detector. */
    public static void onSystemMessage(Component message) {
        LoginSession active = session;
        if (active == null || message == null) return;
        String plain = message.getString();
        if (!settingsLoaded) {
            if (plain.length() <= 2048 && active.isPromptMessage(plain)) queuedPrompt = plain;
            return;
        }
        guard(active, () -> active.onSystemMessage(plain));
    }

    /** Captures an outgoing command without logging or retaining it outside the session. */
    public static boolean onOutgoingCommand(String command) {
        commandHookSeen = true;
        if (Boolean.TRUE.equals(SELF_SENT.get())) return false;
        checkHistoryScrubbing(command);
        LoginSession active = session;
        if (active == null) return false;
        String normalized = command == null ? "" : command.strip();
        if (normalized.matches("(?i)/?(?:[a-z0-9_]+:)?[a-z0-9_]+\\s*")) {
            String name = normalized.replaceFirst("^/", "").strip().toLowerCase(java.util.Locale.ROOT);
            int namespace = name.lastIndexOf(':');
            if (namespace >= 0) name = name.substring(namespace + 1);
            if (patterns != null && patterns.commandNames("login").contains(name)
                    && active.wasShortAliasPrompted(name)) {
                guard(active, () -> active.onOutgoingCommand(command));
                return true;
            }
        }
        guard(active, () -> active.onOutgoingCommand(command));
        return false;
    }

    /** Called by the command-history mixin each time it keeps a sensitive command out of the history. */
    public static void historyScrubbed() { HISTORY_SCRUBBED.set(true); }

    /**
     * When the player types a sensitive command, the history mixin should scrub it within moments. If it did not,
     * the mixin is not applied (for example after a Minecraft update), so the owner is warned once.
     */
    private static void checkHistoryScrubbing(String command) {
        if (warnedHistoryHook || !SensitiveCommands.isSensitive(command)) return;
        boolean scrubbed = HISTORY_SCRUBBED.get();
        HISTORY_SCRUBBED.remove();
        if (!scrubbed) warnOnce(false);
    }

    private static void warnOnce(boolean commandHook) {
        if (commandHook ? warnedCommandHook : warnedHistoryHook) return;
        if (commandHook) warnedCommandHook = true; else warnedHistoryHook = true;
        LOGGER.warn("BlockVault's {} hook is not active in this Minecraft version", commandHook ? "command" : "history");
        VaultRuntime.showToast(commandHook ? "blockvault.toast.hooks_inactive.commands"
                : "blockvault.toast.hooks_inactive.history");
    }

    private static void guard(LoginSession active, Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException failure) {
            LOGGER.warn("BlockVault hit an input error ({})", failure.getClass().getSimpleName());
            if (session != active) return;
            active.onDisconnect();
            // One bad message must not end login handling: start the session over, and only give up after repeats.
            if (++sessionErrors >= 3) {
                LOGGER.warn("BlockVault stopped login handling for this connection after repeated errors");
                session = null;
            } else {
                active.onJoin(sessionHost, sessionUser);
            }
        }
    }

    /** Sends a command generated by BlockVault while suppressing the manual-capture hook. */
    public static void sendOwnCommand(String commandWithoutSlash) {
        Minecraft minecraft = Minecraft.getInstance();
        Runnable send = () -> {
            ClientPacketListener connection = minecraft.getConnection();
            if (connection == null) return;
            SELF_SENT.set(true);
            commandHookSeen = false;
            try { connection.sendCommand(commandWithoutSlash); }
            finally { SELF_SENT.remove(); }
            // The command mixin runs at the start of sendCommand, so it must have run by now.
            if (!commandHookSeen) warnOnce(true);
        };
        if (minecraft.isSameThread()) send.run();
        else minecraft.execute(send);
    }

}
