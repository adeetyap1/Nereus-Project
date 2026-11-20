package my.nereus.intro.config;

import my.nereus.intro.cinematic.Step;
import my.nereus.intro.cinematic.TimelineParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Snapshot config yang tidak berubah. Sesi aktif memegang snapshot-nya sendiri, jadi aman saat reload. */
public final class IntroConfig {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public final boolean enabled;
    public final String prefix;

    public final boolean autoStartEnabled;
    public final int autoStartDelay;
    public final boolean autoStartNewOnly;
    public final boolean replayAllowPlayers;

    public final boolean skipEnabled;
    public final boolean skipAllowPlayers;
    public final int skipMinSeconds;
    public final boolean skipConfirm;
    public final int skipConfirmWindow;

    public final boolean adminAllowTest;
    public final boolean adminTestWhenDisabled;
    public final boolean adminUnlimitedReplay;
    public final boolean adminDebug;
    public final boolean adminCommandsInTest;

    public final LocationSpec room;
    public final LocationSpec spawn;
    public final LocationSpec nerithAbsolute;
    public final String spawnWorldSpawnOf;

    // NPC
    public final boolean npcEnabled;
    public final String npcDisplayName;
    public final float npcScale;
    public final int npcKeepFacing;
    public final boolean npcContinueOnFailure;
    public final boolean npcRelative;
    public final double npcForward;
    public final double npcSide;
    public final double npcYOffset;
    public final String skinMode;
    public final String skinValue;
    public final String skinSignature;
    public final String skinVariant;
    public final boolean outfitEnabled;
    public final String outfitColor;
    public final Map<String, String> outfitSlots; // head/chest/legs/feet -> material

    // Cinematic
    public final boolean lockPlayer;
    public final GameMode gameMode;
    public final boolean isolate;
    public final boolean fadeEnabled;
    public final boolean sounds;
    public final boolean fxEnabled;
    public final String fxSaySound;
    public final String fxSayBedrockSound;
    public final float fxSayVolume;
    public final double fxPitchMin;
    public final double fxPitchMax;
    public final boolean fxParticles;
    public final boolean fxParticlesBedrock;
    public final String fxPlayerSound;
    public final float fxPlayerVolume;
    public final boolean packEnabled;
    public final String packUrl;
    public final String packSha1;
    public final String packPrompt;
    public final boolean packForce;
    public final int packWaitSeconds;
    public final boolean musicEnabled;
    public final net.kyori.adventure.sound.Sound.Source musicSource;
    public final int maxDurationMinutes;
    public final int teleportTimeout;
    public final int minStepDelay;
    public final int pauseBase;
    public final double pausePerChar;
    public final int pauseMin;
    public final int pauseMax;
    public final String fmtDialogue;
    public final String fmtPlayer;
    public final String fmtNarration;
    public final String fmtActionbar;

    public final boolean bedrockSpawnNpc;
    public final boolean bedrockFallbackSounds;

    public final boolean recoveryEnabled;
    public final boolean recoveryRestoreOnJoin;
    public final boolean recoveryRestart;
    public final int recoveryMaxRestarts;
    public final int recoveryRestartDelay;

    public final boolean blockCommands;
    public final List<String> allowedCommands;

    private final Map<String, String> messages;
    public final List<Step> timeline;

    public IntroConfig(FileConfiguration c, Logger log) {
        enabled = c.getBoolean("enabled", true);
        prefix = c.getString("prefix", "");

        autoStartEnabled = c.getBoolean("auto-start.enabled", true);
        autoStartDelay = Math.max(1, c.getInt("auto-start.delay-ticks", 60));
        autoStartNewOnly = c.getBoolean("auto-start.new-players-only", true);
        replayAllowPlayers = c.getBoolean("replay.allow-players", false);

        skipEnabled = c.getBoolean("skip.enabled", true);
        skipAllowPlayers = c.getBoolean("skip.allow-players", true);
        skipMinSeconds = Math.max(0, c.getInt("skip.min-seconds-before-skip", 5));
        skipConfirm = c.getBoolean("skip.require-confirm", true);
        skipConfirmWindow = Math.max(1, c.getInt("skip.confirm-window-seconds", 10));

        adminAllowTest = c.getBoolean("admin.allow-test", true);
        adminTestWhenDisabled = c.getBoolean("admin.test-works-when-disabled", true);
        adminUnlimitedReplay = c.getBoolean("admin.unlimited-replay", true);
        adminDebug = c.getBoolean("admin.debug-in-test", true);
        adminCommandsInTest = c.getBoolean("admin.allow-commands-in-test", true);

        room = LocationSpec.read(c.getConfigurationSection("locations.room"));
        spawn = LocationSpec.read(c.getConfigurationSection("locations.spawn"));
        nerithAbsolute = LocationSpec.read(c.getConfigurationSection("locations.nerith"));
        spawnWorldSpawnOf = c.getString("locations.spawn.use-world-spawn-of", "");

        npcEnabled = c.getBoolean("npc.enabled", true);
        npcDisplayName = c.getString("npc.display-name", "<white><bold>NERITH</bold></white>");
        npcScale = (float) Math.max(0.1, c.getDouble("npc.scale", 1.0));
        npcKeepFacing = Math.max(0, c.getInt("npc.keep-facing-interval-ticks", 20));
        npcContinueOnFailure = !"abort".equalsIgnoreCase(c.getString("npc.on-failure", "continue"));
        npcRelative = !"absolute".equalsIgnoreCase(c.getString("npc.position.mode", "relative"));
        npcForward = c.getDouble("npc.position.forward", 3.0);
        npcSide = c.getDouble("npc.position.side", 0.0);
        npcYOffset = c.getDouble("npc.position.y-offset", 0.0);
        skinMode = c.getString("npc.skin.mode", "default").toLowerCase(Locale.ROOT);
        skinValue = c.getString("npc.skin.value", "");
        skinSignature = c.getString("npc.skin.signature", "");
        skinVariant = c.getString("npc.skin.variant", "AUTO");
        outfitEnabled = c.getBoolean("npc.white-outfit.enabled", true);
        outfitColor = c.getString("npc.white-outfit.color", "#FFFFFF");
        Map<String, String> slots = new HashMap<>();
        for (String k : new String[]{"head", "chest", "legs", "feet"}) {
            slots.put(k, c.getString("npc.white-outfit." + k, ""));
        }
        outfitSlots = Map.copyOf(slots);

        lockPlayer = c.getBoolean("cinematic.lock-player", true);
        GameMode gm;
        try {
            gm = GameMode.valueOf(c.getString("cinematic.game-mode", "ADVENTURE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            gm = GameMode.ADVENTURE;
        }
        gameMode = gm;
        isolate = c.getBoolean("cinematic.isolate-from-others", true);
        fadeEnabled = c.getBoolean("cinematic.fade.enabled", true);
        sounds = c.getBoolean("cinematic.sounds", true);
        fxEnabled = c.getBoolean("cinematic.dialogue-effects.enabled", true);
        fxSaySound = c.getString("cinematic.dialogue-effects.say-sound", "minecraft:block.amethyst_block.chime");
        fxSayBedrockSound = c.getString("cinematic.dialogue-effects.say-bedrock-sound", "");
        fxSayVolume = (float) c.getDouble("cinematic.dialogue-effects.say-volume", 0.25);
        fxPitchMin = c.getDouble("cinematic.dialogue-effects.pitch-min", 1.4);
        fxPitchMax = c.getDouble("cinematic.dialogue-effects.pitch-max", 1.9);
        fxParticles = c.getBoolean("cinematic.dialogue-effects.particles", true);
        fxParticlesBedrock = c.getBoolean("cinematic.dialogue-effects.particles-on-bedrock", true);
        fxPlayerSound = c.getString("cinematic.dialogue-effects.player-sound", "minecraft:block.note_block.hat");
        fxPlayerVolume = (float) c.getDouble("cinematic.dialogue-effects.player-volume", 0.2);
        packEnabled = c.getBoolean("resource-pack.enabled", false);
        packUrl = c.getString("resource-pack.url", "");
        packSha1 = c.getString("resource-pack.sha1", "");
        packPrompt = c.getString("resource-pack.prompt", "<gray>Musik dan suara intro Nereus");
        packForce = c.getBoolean("resource-pack.force", false);
        packWaitSeconds = Math.max(3, c.getInt("resource-pack.wait-seconds", 15));
        musicEnabled = c.getBoolean("cinematic.music.enabled", true);
        net.kyori.adventure.sound.Sound.Source src;
        try {
            src = net.kyori.adventure.sound.Sound.Source.valueOf(c.getString("cinematic.music.category", "RECORD").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            src = net.kyori.adventure.sound.Sound.Source.RECORD;
        }
        musicSource = src;
        maxDurationMinutes = Math.max(1, c.getInt("cinematic.max-duration-minutes", 15));
        teleportTimeout = Math.max(20, c.getInt("cinematic.teleport-timeout-ticks", 200));
        minStepDelay = Math.max(1, c.getInt("cinematic.min-step-delay-ticks", 1));
        pauseBase = Math.max(0, c.getInt("cinematic.pause.base-ticks", 30));
        pausePerChar = Math.max(0, c.getDouble("cinematic.pause.per-char-ticks", 0.9));
        pauseMin = Math.max(1, c.getInt("cinematic.pause.min-ticks", 45));
        pauseMax = Math.max(pauseMin, c.getInt("cinematic.pause.max-ticks", 180));
        fmtDialogue = c.getString("cinematic.format.dialogue", "<white><bold>NERITH</bold> <dark_gray>»</dark_gray> <text>");
        fmtPlayer = c.getString("cinematic.format.player", "<yellow><player></yellow> <dark_gray>»</dark_gray> <gray><text>");
        fmtNarration = c.getString("cinematic.format.narration", "<gray><italic><text></italic>");
        fmtActionbar = c.getString("cinematic.format.actionbar", "<gray><text>");

        bedrockSpawnNpc = c.getBoolean("bedrock.spawn-npc", true);
        bedrockFallbackSounds = c.getBoolean("bedrock.use-fallback-sounds", true);

        recoveryEnabled = c.getBoolean("recovery.enabled", true);
        recoveryRestoreOnJoin = c.getBoolean("recovery.restore-on-join", true);
        recoveryRestart = c.getBoolean("recovery.restart-interrupted", true);
        recoveryMaxRestarts = Math.max(0, c.getInt("recovery.max-restarts", 2));
        recoveryRestartDelay = Math.max(1, c.getInt("recovery.restart-delay-ticks", 60));

        blockCommands = c.getBoolean("protection.block-commands", true);
        List<String> allowed = new ArrayList<>();
        for (String s : c.getStringList("protection.allowed-commands")) allowed.add(s.toLowerCase(Locale.ROOT));
        allowedCommands = List.copyOf(allowed);

        Map<String, String> msgs = new HashMap<>();
        ConfigurationSection ms = c.getConfigurationSection("messages");
        if (ms != null) for (String k : ms.getKeys(false)) msgs.put(k, ms.getString(k, ""));
        messages = Map.copyOf(msgs);

        timeline = TimelineParser.parse(c.getMapList("timeline"), log);
    }

    /** Pesan dengan prefix. */
    public Component message(String key, TagResolver... resolvers) {
        String raw = messages.getOrDefault(key, key);
        return MM.deserialize(prefix + raw, resolvers);
    }

    public Location resolveSpawn() {
        if (spawnWorldSpawnOf != null && !spawnWorldSpawnOf.isBlank()) {
            World w = Bukkit.getWorld(spawnWorldSpawnOf);
            return w == null ? null : w.getSpawnLocation();
        }
        return spawn.resolve();
    }

    /** Daftar masalah lokasi; kosong berarti siap. */
    public List<String> locationProblems() {
        List<String> p = new ArrayList<>();
        if (!room.isConfigured()) p.add("locations.room belum diisi");
        else if (room.resolve() == null) p.add("world ruang putih '" + room.world() + "' tidak dimuat");
        boolean useWs = spawnWorldSpawnOf != null && !spawnWorldSpawnOf.isBlank();
        if (useWs) {
            if (Bukkit.getWorld(spawnWorldSpawnOf) == null) p.add("world spawn '" + spawnWorldSpawnOf + "' tidak dimuat");
        } else if (!spawn.isConfigured()) p.add("locations.spawn belum diisi");
        else if (spawn.resolve() == null) p.add("world spawn '" + spawn.world() + "' tidak dimuat");
        if (!npcRelative && npcEnabled) {
            if (nerithAbsolute.resolve() == null) p.add("locations.nerith belum valid (mode absolute)");
        }
        return p;
    }
}
