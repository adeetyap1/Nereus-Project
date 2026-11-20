package my.nereus.intro.session;

import my.nereus.intro.NereusIntro;
import my.nereus.intro.cinematic.CinematicRunner;
import my.nereus.intro.cinematic.FadeEffect;
import my.nereus.intro.cinematic.MusicPlayer;
import my.nereus.intro.cinematic.SoundLayers;
import my.nereus.intro.config.IntroConfig;
import my.nereus.intro.npc.NerithNpc;
import my.nereus.intro.recovery.PlayerSnapshot;
import my.nereus.intro.recovery.SafeTeleport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Seluruh state satu intro milik satu pemain. Semua task dan NPC dibersihkan di {@link #finish(EndReason)}. */
public final class IntroSession {

    private final NereusIntro plugin;
    private final Player player;
    private final UUID uuid;
    private final boolean test;
    private final IntroConfig cfg;
    private final PlayerSnapshot snapshot;
    private final Boolean bedrockOverride;
    private final long startedAt = System.currentTimeMillis();

    private final AtomicBoolean ended = new AtomicBoolean(false);
    private final List<BukkitTask> tasks = new CopyOnWriteArrayList<>();
    private CinematicRunner runner;
    private MusicPlayer music;
    private SoundLayers layers;
    private boolean atSpawn;
    private boolean faded;
    private boolean bedrock;
    private long skipConfirmUntil;

    public IntroSession(NereusIntro plugin, Player player, boolean test, IntroConfig cfg,
                        PlayerSnapshot snapshot, Boolean bedrockOverride) {
        this.plugin = plugin;
        this.player = player;
        this.uuid = player.getUniqueId();
        this.test = test;
        this.cfg = cfg;
        this.snapshot = snapshot;
        this.bedrockOverride = bedrockOverride;
    }

    public UUID uuid() { return uuid; }
    public Player player() { return player; }
    public boolean isTest() { return test; }
    public IntroConfig config() { return cfg; }
    public boolean isBedrock() { return bedrock; }
    public boolean isEnded() { return ended.get(); }
    public long startedAt() { return startedAt; }
    public boolean locked() { return cfg.lockPlayer && !ended.get(); }

    public long skipConfirmUntil() { return skipConfirmUntil; }
    public void skipConfirmUntil(long t) { this.skipConfirmUntil = t; }

    public MusicPlayer music() {
        if (music == null) music = new MusicPlayer(plugin, player);
        return music;
    }

    public SoundLayers layers() {
        if (layers == null) layers = new SoundLayers(plugin, player);
        return layers;
    }

    public void track(BukkitTask t) { if (t != null) tasks.add(t); }
    public void markAtSpawn() { this.atSpawn = true; }
    public void setFaded(boolean f) { this.faded = f; }

    // ---------------------------------------------------------------- mulai

    public void start() {
        bedrock = bedrockOverride != null ? bedrockOverride : plugin.platform().isBedrock(player);
        plugin.resourcePacks().ensure(this, this::begin);
    }

    /** Dipanggil setelah pack musik siap (atau dilewati). */
    private void begin() {
        if (ended.get()) return;
        if (cfg.recoveryEnabled) plugin.recovery().store().save(uuid, snapshot, test);

        try {
            player.leaveVehicle();
            if (cfg.lockPlayer) {
                player.setGameMode(cfg.gameMode);
                player.setFlying(false);
                player.setAllowFlight(false);
                player.setWalkSpeed(0f);
                player.setFlySpeed(0f);
            }
            player.setInvulnerable(true);
        } catch (Throwable t) {
            fail("tidak bisa menyiapkan pemain: " + t.getMessage());
            return;
        }
        if (cfg.isolate) plugin.isolation().apply(player);

        track(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getLogger().warning("Sesi intro " + player.getName() + " melewati batas waktu.");
            player.sendMessage(cfg.message("timed-out"));
            finish(EndReason.FAILED);
        }, cfg.maxDurationMinutes * 60L * 20L));

        if (test) {
            player.sendMessage(cfg.message("test-started"));
            boolean packOk = plugin.resourcePacks().isLoaded(uuid);
            player.sendMessage(net.kyori.adventure.text.Component.text("[TEST] Platform: " + (bedrock ? "Bedrock" : "Java")
                    + (bedrock ? " | musik memakai .mcpack Geyser (plugins/Geyser-Spigot/packs)"
                    : " | resource pack dari plugin: " + (packOk ? "termuat" : "BELUM termuat (musik hening)"))));
        }
        runner = new CinematicRunner(plugin, this, cfg.timeline);
        runner.start();
    }

    public void onTimelineFinished() {
        finish(EndReason.COMPLETED);
    }

    public void fail(String reason) {
        if (ended.get()) return;
        plugin.getLogger().warning("Intro " + player.getName() + " dihentikan: " + reason);
        if (player.isOnline()) player.sendMessage(cfg.message("failed"));
        finish(EndReason.FAILED);
    }

    public void debugStep(int index, int size, String name) {
        if (test && cfg.adminDebug && player.isOnline()) {
            player.sendActionBar(Component.text("[TEST] " + index + "/" + size + " " + name));
        }
    }

    // ---------------------------------------------------------------- NPC

    /** @return true bila NPC berhasil dibuat. */
    public boolean spawnNpc() {
        if (ended.get()) return false;
        if (!plugin.npcService().isAvailable()) {
            plugin.getLogger().warning("FancyNpcs tidak tersedia; NPC Nerith dilewati.");
            return false;
        }
        try {
            NerithNpc npc = plugin.npcService().spawn(player, cfg);
            npc.lookAt(player);
            if (cfg.npcKeepFacing > 0) {
                track(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (ended.get() || !player.isOnline()) return;
                    npcLook();
                }, cfg.npcKeepFacing, cfg.npcKeepFacing));
            }
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Gagal membuat NPC Nerith (cek versi FancyNpcs): " + t);
            return false;
        }
    }

    public void npcLook() {
        NerithNpc npc = plugin.npcService().get(uuid);
        if (npc != null && player.isOnline()) npc.lookAt(player);
    }

    public void removeNpc() {
        plugin.npcService().remove(uuid);
    }

    // ---------------------------------------------------------------- selesai

    /** Idempotent. Membersihkan task/NPC/isolasi lalu memulihkan pemain sesuai alasan. */
    public void finish(EndReason reason) {
        if (!ended.compareAndSet(false, true)) return;

        if (runner != null) runner.stop();
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        plugin.resourcePacks().cancel(uuid);
        if (music != null) music.stop();
        if (layers != null) layers.stopAll();
        plugin.npcService().remove(uuid);
        plugin.isolation().release(player);
        if (faded || player.isOnline()) {
            try {
                FadeEffect.clear(player);
            } catch (Throwable ignored) {
                // abaikan
            }
        }

        // progres (test tidak pernah mengubah progres)
        if (!test) {
            if (reason.finishesStory()) plugin.progress().markCompleted(uuid);
            else if (reason.syncRestore()) plugin.progress().addInterrupted(uuid);
        }

        boolean toSpawn = !test && reason.finishesStory();
        List<Location> targets = new ArrayList<>();
        if (toSpawn) {
            targets.add(cfg.resolveSpawn());
            targets.add(snapshot.location());
        } else {
            targets.add(snapshot.location());
            targets.add(cfg.resolveSpawn());
        }
        targets.add(SafeTeleport.lastResort());

        plugin.sessions().remove(uuid);

        Runnable after = () -> restoreState(reason);
        if (toSpawn && atSpawn) {
            after.run();
        } else {
            SafeTeleport.chain(plugin, player, targets, reason.syncRestore(), ok -> {
                if (!ok) {
                    plugin.getLogger().warning("Teleport pemulihan " + player.getName() + " gagal; file recovery dipertahankan.");
                    return;
                }
                after.run();
            });
        }
    }

    private void restoreState(EndReason reason) {
        try {
            snapshot.applyState(player);
            plugin.recovery().store().delete(uuid);
            if (!player.isOnline() && !reason.syncRestore()) return;
            if (reason == EndReason.QUIT || reason == EndReason.DISABLE) return;
            if (test) player.sendMessage(cfg.message("test-ended"));
            else if (reason == EndReason.SKIPPED) player.sendMessage(cfg.message("skipped"));
        } catch (Throwable t) {
            logError(t);
        }
    }

    public void logError(Throwable t) {
        plugin.getLogger().warning("Error sesi intro " + player.getName() + ": " + t);
    }

    public Component msg(String key, TagResolver... r) {
        return cfg.message(key, r);
    }
}
