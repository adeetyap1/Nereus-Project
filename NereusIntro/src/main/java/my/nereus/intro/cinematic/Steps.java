package my.nereus.intro.cinematic;

import my.nereus.intro.config.IntroConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.function.LongConsumer;

/** Implementasi seluruh jenis langkah timeline. */
public final class Steps {

    private Steps() {}

    /** wait &lt; 0 berarti memakai jeda bawaan langkah. */
    private abstract static class Base implements Step {
        protected final int wait;

        Base(int wait) { this.wait = wait; }

        protected long delay(long def) { return wait >= 0 ? wait : def; }
    }

    // ------------------------------------------------------------------ teks

    public static final class Narrate extends Base {
        private final String text;

        public Narrate(String text, int wait) { super(wait); this.text = text; }

        @Override
        public void execute(StepContext ctx, LongConsumer done) {
            Player p = ctx.player();
            Component c = TextFormatter.formatted(ctx.cfg().fmtNarration, text, p);
            p.sendMessage(c);
            done.accept(delay(ctx.pauseFor(c)));
        }

        @Override public String name() { return "narrate"; }
    }

    public static final class Say extends Base {
        private final String text;

        public Say(String text, int wait) { super(wait); this.text = text; }

        @Override
        public void execute(StepContext ctx, LongConsumer done) {
            Player p = ctx.player();
            Component c = TextFormatter.formatted(ctx.cfg().fmtDialogue, text, p);
            p.sendMessage(c);
            ctx.session().npcLook();
            done.accept(delay(ctx.pauseFor(c)));
        }

        @Override public String name() { return "say"; }
    }

    public static final class PlayerLine extends Base {
        private final String text;

        public PlayerLine(String text, int wait) { super(wait); this.text = text; }

        @Override
        public void execute(StepContext ctx, LongConsumer done) {
            Player p = ctx.player();
            Component c = TextFormatter.formatted(ctx.cfg().fmtPlayer, text, p);
            p.sendMessage(c);
            done.accept(delay(Math.max(ctx.cfg().pauseMin, ctx.pauseFor(c) - 15)));
        }

        @Override public String name() { return "player"; }
    }

    public static final class ActionBar extends Base {
        private final String text;

        public ActionBar(String text, int wait) { super(wait); this.text = text; }

        @Override
        public void execute(StepContext ctx, LongConsumer done) {
            ctx.player().sendActionBar(TextFormatter.formatted(ctx.cfg().fmtActionbar, text, ctx.player()));
            done.accept(delay(40));
        }

        @Override public String name() { return "actionbar"; }
    }

    public static final class TitleStep extends Base {
        private final String title;
        private final String subtitle;
        private final int fadeIn;
        private final int stay;
        private final int fadeOut;

        public TitleStep(String title, String subtitle, int fadeIn, int stay, int fadeOut, int wait) {
            super(wait);
            this.title = title;
            this.subtitle = subtitle;
            this.fadeIn = fadeIn;
            this.stay = stay;
            this.fadeOut = fadeOut;
        }

        @Override
        public void execute(StepContext ctx, LongConsumer done) {
            Player p = ctx.player();
            Title.Times times = Title.Times.times(
                    Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L));
            p.showTitle(Title.title(TextFormatter.plain(title, p), TextFormatter.plain(subtitle, p), times));
            done.accept(delay(fadeIn + stay));
        }

        @Override public String name() { return "title"; }
    }

    // ------------------------------------------------------------------ audio / efek

    public static final class Sound extends Base {
        private final String sound;
        private final String bedrockSound;
        private final float volume;
        private final float pitch;

        public Sound(String sound, String bedrockSound, float volume, float pitch, int wait) {
            super(wait);
            this.sound = sound;
            this.bedrockSound = bedrockSound;
            this.volume = volume;
            this.pitch = pitch;
        }

        @Override
        public void execute(StepContext ctx, LongConsumer done) {
            IntroConfig cfg = ctx.cfg();
            if (cfg.sounds) {
                String key = sound;
                if (ctx.bedrock() && cfg.bedrockFallbackSounds && bedrockSound != null && !bedrockSound.isBlank()) {
                    key = bedrockSound;
                }
                SoundPlayer.play(ctx.player(), key, volume, pitch);
            }
            done.accept(delay(0));
        }

        @Override public String name() { return "sound"; }
    }

    public static final class Fade extends Base {
        private final boolean out;
        private final int ticks;

        public Fade(boolean out, int ticks, int wait) {
            super(wait);
            this.out = out;
            this.ticks = ticks;
        }

        @Override
        public void execute(StepContext ctx, LongConsumer done) {
            if (ctx.cfg().fadeEnabled) {
                if (out) {
                    FadeEffect.fadeOut(ctx.player());
                    ctx.session().setFaded(true);
                } else {
                    FadeEffect.fadeIn(ctx.player(), ticks);
                }
            }
            done.accept(delay(ticks));
        }

        @Override public String name() { return out ? "fade-out" : "fade-in"; }
    }

    public static final class Wait extends Base {
        public Wait(int ticks) { super(ticks); }

        @Override
        public void execute(StepContext ctx, LongConsumer done) { done.accept(delay(0)); }

        @Override public String name() { return "wait"; }
    }

    // ------------------------------------------------------------------ posisi

    public static final class Teleport extends Base {
        private final boolean toSpawn;

        public Teleport(boolean toSpawn, int wait) { super(wait); this.toSpawn = toSpawn; }

        @Override
        public void execute(StepContext ctx, LongConsumer done) {
            IntroConfig cfg = ctx.cfg();
            Location target = toSpawn ? cfg.resolveSpawn() : cfg.room.resolve();
            if (target == null) {
                ctx.fail("lokasi " + (toSpawn ? "spawn" : "ruang putih") + " tidak valid");
                return;
            }
            Player p = ctx.player();
            BukkitTask watchdog = Bukkit.getScheduler().runTaskLater(ctx.plugin(),
                    () -> ctx.fail("teleport melewati batas waktu"), cfg.teleportTimeout);
            ctx.track(watchdog);
            try {
                p.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN).whenComplete((ok, err) -> {
                    Runnable r = () -> {
                        watchdog.cancel();
                        if (err != null || !Boolean.TRUE.equals(ok)) {
                            ctx.fail("teleport gagal");
                            return;
                        }
                        if (toSpawn) ctx.session().markAtSpawn();
                        done.accept(delay(10));
                    };
                    if (Bukkit.isPrimaryThread()) r.run();
                    else {
                        try {
                            Bukkit.getScheduler().runTask(ctx.plugin(), r);
                        } catch (Throwable ignored) {
                            // plugin dimatikan
                        }
                    }
                });
            } catch (Throwable t) {
                watchdog.cancel();
                ctx.fail("teleport error: " + t.getMessage());
            }
        }

        @Override public String name() { return toSpawn ? "teleport-spawn" : "teleport-room"; }
    }

    public static final class NpcAction extends Base {
        public enum Kind { SPAWN, LOOK, REMOVE }

        private final Kind kind;

        public NpcAction(Kind kind, int wait) { super(wait); this.kind = kind; }

        @Override
        public void execute(StepContext ctx, LongConsumer done) {
            switch (kind) {
                case SPAWN -> {
                    IntroConfig cfg = ctx.cfg();
                    if (!cfg.npcEnabled || (ctx.bedrock() && !cfg.bedrockSpawnNpc)) {
                        done.accept(delay(0));
                        return;
                    }
                    if (!ctx.session().spawnNpc()) {
                        if (!cfg.npcContinueOnFailure) {
                            ctx.fail("NPC Nerith gagal dibuat");
                            return;
                        }
                    }
                    done.accept(delay(20));
                }
                case LOOK -> {
                    ctx.session().npcLook();
                    done.accept(delay(0));
                }
                case REMOVE -> {
                    ctx.session().removeNpc();
                    done.accept(delay(0));
                }
            }
        }

        @Override public String name() { return "npc-" + kind.name().toLowerCase(java.util.Locale.ROOT); }
    }
}
