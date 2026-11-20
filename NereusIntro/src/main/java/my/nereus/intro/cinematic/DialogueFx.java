package my.nereus.intro.cinematic;

import my.nereus.intro.config.IntroConfig;
import my.nereus.intro.npc.NerithNpc;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/** Efek halus saat dialog: bunyi lembut dan sedikit partikel cahaya di sekitar Nerith. Sengaja dibuat tipis. */
public final class DialogueFx {

    private DialogueFx() {}

    public static void say(StepContext ctx) {
        IntroConfig c = ctx.cfg();
        if (!c.fxEnabled) return;
        Player p = ctx.player();
        if (c.sounds) {
            String key = ctx.bedrock() && !c.fxSayBedrockSound.isBlank() ? c.fxSayBedrockSound : c.fxSaySound;
            double lo = Math.min(c.fxPitchMin, c.fxPitchMax), hi = Math.max(c.fxPitchMin, c.fxPitchMax);
            float pitch = (float) (lo + ThreadLocalRandom.current().nextDouble() * (hi - lo));
            SoundPlayer.play(p, key, c.fxSayVolume, pitch);
        }
        if (c.fxParticles && (!ctx.bedrock() || c.fxParticlesBedrock)) {
            NerithNpc npc = ctx.plugin().npcService().get(p.getUniqueId());
            if (npc != null && npc.location() != null) {
                try {
                    Location l = npc.location().clone().add(0, 2.0, 0);
                    p.spawnParticle(Particle.END_ROD, l, 4, 0.25, 0.15, 0.25, 0.005);
                } catch (Throwable ignored) {
                    // kosmetik saja
                }
            }
        }
    }

    public static void player(StepContext ctx) {
        IntroConfig c = ctx.cfg();
        if (!c.fxEnabled || !c.sounds || c.fxPlayerSound.isBlank()) return;
        SoundPlayer.play(ctx.player(), c.fxPlayerSound, c.fxPlayerVolume, 1.2f);
    }
}
