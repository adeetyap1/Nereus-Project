package com.nereus.guildmaster.adventurer;

import com.nereus.guildmaster.GuildMaster;
import com.nereus.guildmaster.db.AdventurerRepository;
import com.nereus.guildmaster.rpg.RpgAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Cache catatan registrasi dan aturan pendaftaran. Database selalu async; callback kembali ke main thread. */
public final class AdventurerManager {
    public enum Result { OK, ALREADY, REVOKED, RANK_TOO_LOW, RPG_UNAVAILABLE, ERROR }

    private final GuildMaster plugin;
    private final AdventurerRepository repo;
    private final Map<UUID, Adventurer> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> loaded = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> registering = new ConcurrentHashMap<>();

    public AdventurerManager(GuildMaster plugin, AdventurerRepository repo) {
        this.plugin = plugin;
        this.repo = repo;
    }

    // ------------------------------------------------------------------ cache

    /** Muat catatan pemain (async). Callback di main thread, boleh null. */
    public void load(Player p, Runnable then) {
        UUID id = p.getUniqueId();
        repo.load(id).whenComplete((adv, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.getLogger().warning("Gagal memuat data guild " + p.getName() + ": " + err.getMessage());
            } else {
                if (adv == null) {
                    cache.remove(id);
                } else {
                    cache.put(id, adv);
                    if (!adv.name().equals(p.getName())) {
                        cache.put(id, new Adventurer(id, p.getName(), adv.registeredAt(), adv.revoked()));
                        repo.updateName(id, p.getName());
                    }
                }
                loaded.put(id, true);
            }
            if (then != null) {
                then.run();
            }
        }));
    }

    public void unload(UUID id) {
        cache.remove(id);
        loaded.remove(id);
        registering.remove(id);
    }

    public void clearCache() {
        cache.clear();
        loaded.clear();
    }

    /** Pastikan data termuat lalu jalankan aksi (di main thread). */
    public void ready(Player p, Runnable action) {
        if (loaded.containsKey(p.getUniqueId())) {
            action.run();
        } else {
            load(p, () -> {
                if (p.isOnline()) {
                    action.run();
                }
            });
        }
    }

    public Adventurer get(UUID id) {
        return cache.get(id);
    }

    public boolean isRegistered(UUID id) {
        Adventurer a = cache.get(id);
        return a != null && a.active();
    }

    // ------------------------------------------------------------------ aturan

    public int minRankIndex(RpgAdapter rpg) {
        String key = plugin.getConfig().getString("registration.min-rank", "F");
        return rpg.rankIndex(key);
    }

    /** Daftarkan pemain sendiri. Memeriksa RPG, rank minimum, dan duplikasi. */
    public void register(Player p, Consumer<Result> done) {
        UUID id = p.getUniqueId();
        RpgAdapter rpg = plugin.rpg();
        if (!rpg.available()) {
            done.accept(Result.RPG_UNAVAILABLE);
            return;
        }
        Adventurer existing = cache.get(id);
        if (existing != null) {
            done.accept(existing.active() ? Result.ALREADY : Result.REVOKED);
            return;
        }
        RpgAdapter.RpgProfile prof = rpg.profile(p);
        int min = minRankIndex(rpg);
        if (prof == null || min < 0 || prof.rankIndex() < min) {
            done.accept(Result.RANK_TOO_LOW);
            return;
        }
        if (registering.putIfAbsent(id, true) != null) {
            done.accept(Result.ALREADY);
            return;
        }
        Adventurer adv = new Adventurer(id, p.getName(), System.currentTimeMillis(), false);
        repo.insert(adv).whenComplete((inserted, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            registering.remove(id);
            if (err != null) {
                plugin.getLogger().warning("Registrasi gagal untuk " + p.getName() + ": " + err.getMessage());
                done.accept(Result.ERROR);
            } else if (inserted) {
                cache.put(id, adv);
                done.accept(Result.OK);
            } else {
                // sudah ada di database (mungkin dicabut): muat ulang agar status akurat
                load(p, () -> done.accept(isRegistered(id) ? Result.ALREADY : Result.REVOKED));
            }
        }));
    }

    // ------------------------------------------------------------------ admin

    /** Daftarkan paksa (melewati syarat rank) atau pulihkan yang dicabut. */
    public void adminRegister(UUID id, String name, Consumer<Boolean> done) {
        repo.insert(new Adventurer(id, name, System.currentTimeMillis(), false)).whenComplete((ins, err) -> {
            if (err != null) {
                Bukkit.getScheduler().runTask(plugin, () -> done.accept(false));
            } else if (ins) {
                finishAdmin(id, name, done);
            } else {
                repo.setRevoked(id, false).whenComplete((ok, e2) -> finishAdmin(id, name, done));
            }
        });
    }

    private void finishAdmin(UUID id, String name, Consumer<Boolean> done) {
        repo.load(id).whenComplete((adv, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (adv != null) {
                if (Bukkit.getPlayer(id) != null) {
                    cache.put(id, adv);
                    loaded.put(id, true);
                }
            }
            done.accept(err == null && adv != null);
        }));
    }

    /** Cabut akses (catatan tetap ada, pemain tidak bisa mendaftar sendiri lagi). */
    public void adminUnregister(UUID id, Consumer<Boolean> done) {
        repo.setRevoked(id, true).whenComplete((ok, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            Adventurer a = cache.get(id);
            if (a != null) {
                cache.put(id, new Adventurer(id, a.name(), a.registeredAt(), true));
            }
            done.accept(err == null && Boolean.TRUE.equals(ok));
        }));
    }

    /** Hapus catatan sepenuhnya: seolah belum pernah mendaftar. */
    public void adminReset(UUID id, Consumer<Boolean> done) {
        repo.delete(id).whenComplete((ok, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            cache.remove(id);
            if (Bukkit.getPlayer(id) != null) {
                loaded.put(id, true);
            } else {
                loaded.remove(id);
            }
            done.accept(err == null && Boolean.TRUE.equals(ok));
        }));
    }

    public void count(Consumer<Integer> done) {
        repo.count().whenComplete((n, err) -> Bukkit.getScheduler().runTask(plugin, () -> done.accept(err == null ? n : -1)));
    }
}
