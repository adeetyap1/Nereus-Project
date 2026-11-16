package com.nereus.rpg.guild;

import com.nereus.rpg.Core;
import com.nereus.rpg.Defs;
import com.nereus.rpg.GuildStatus;
import com.nereus.rpg.Menus;
import com.nereus.rpg.NerusRPG;
import com.nereus.rpg.Profile;
import com.nereus.rpg.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Logika Nerus Guild: pendaftaran, pencabutan, pemulihan, dan pengaturan rank oleh admin. */
public final class Guild {
    public enum Result { OK, ALREADY, REVOKED, NOT_REGISTERED, NOT_REVOKED, NOT_FOUND, UNKNOWN_RANK }

    private final NerusRPG plugin;
    private final Core core;
    public final Messages msgs;
    public final Dialogue dialogue;

    public Guild(NerusRPG plugin, Core core) {
        this.plugin = plugin;
        this.core = core;
        this.msgs = new Messages(plugin);
        this.dialogue = new Dialogue(plugin, core, this);
    }

    public void load() {
        msgs.load();
        dialogue.load();
    }

    public String date(long millis) {
        if (millis <= 0) {
            return "-";
        }
        String fmt = plugin.getConfig().getString("date-format", "dd MMM yyyy");
        try {
            return new SimpleDateFormat(fmt).format(new Date(millis));
        } catch (IllegalArgumentException e) {
            return new SimpleDateFormat("dd MMM yyyy").format(new Date(millis));
        }
    }

    public String statusLabel(GuildStatus s) {
        return msgs.get("status-" + s.name().toLowerCase());
    }

    /** Pesan singkat untuk non-anggota yang memakai fitur RPG. */
    public void notMember(Player p) {
        Profile pr = core.profile(p);
        p.sendMessage(Text.c(Text.PREFIX + msgs.get(pr.status == GuildStatus.REVOKED ? "revoked-block" : "not-member")));
    }

    // ------------------------------------------------------------------ muat data (online/offline)

    /** Ambil profil pemain (hidup jika online, atau dibaca async). Callback di main thread; null jika tidak ada data. */
    public void load(UUID id, String name, boolean create, Consumer<Profile> then) {
        Profile live = core.loaded(id);
        if (live != null) {
            then.accept(live);
            return;
        }
        core.store.readAsync(plugin, id, p -> {
            Profile cur = core.loaded(id);
            if (cur != null) {
                then.accept(cur);
                return;
            }
            Profile use = p;
            if (use == null && create) {
                use = new Profile(id);
                use.name = name;
            }
            then.accept(use);
        });
    }

    // ------------------------------------------------------------------ pendaftaran

    /** Pemain mendaftar sendiri lewat Caylin. */
    public Result register(Player p) {
        Profile pr = core.profile(p);
        if (pr.status == GuildStatus.REGISTERED) {
            return Result.ALREADY;
        }
        if (pr.status == GuildStatus.REVOKED) {
            return Result.REVOKED;
        }
        doRegister(pr, "PLAYER");
        afterRegister(p, pr);
        return Result.OK;
    }

    /** Admin mendaftarkan pemain (online atau offline). */
    public Result adminRegister(Profile pr, Player online) {
        if (pr.status == GuildStatus.REGISTERED) {
            return Result.ALREADY;
        }
        if (pr.status == GuildStatus.REVOKED) {
            return Result.REVOKED;
        }
        doRegister(pr, "ADMIN");
        if (online != null) {
            afterRegister(online, pr);
        } else {
            core.save(pr);
        }
        return Result.OK;
    }

    private void doRegister(Profile pr, String by) {
        boolean fresh = pr.level < 1;
        pr.status = GuildStatus.REGISTERED;
        pr.registeredAt = System.currentTimeMillis();
        pr.registeredBy = by;
        if (fresh) {
            pr.level = 1;
            pr.xp = 0;
            pr.totalXp = 0;
            pr.rank = 0;
            pr.mana = -1;
        }
        pr.restoredNotice = false;
        core.logRank(pr, "NONE", core.rank(pr.rank) == null ? "F" : core.rank(pr.rank).key(), "REGISTER");
    }

    private void afterRegister(Player p, Profile pr) {
        core.checkDay(pr);
        if (pr.mana < 0) {
            pr.mana = core.maxMana(pr);
        }
        core.applyBuffs(p);
        core.syncRank(p, "NONE");
        core.runConsole(p, plugin.getConfig().getStringList("registration.commands"));
        core.save(pr);
        p.showTitle(Title.title(Text.c(msgs.get("register-title")), Text.c(msgs.get("register-subtitle"))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.sendMessage(Text.c(Text.PREFIX + msgs.get("register-ok", Map.of("rank", core.label(core.rank(pr.rank), p)))));
        if (pr.cls == null && plugin.getConfig().getBoolean("intro.force-class-choice", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && core.profile(p).cls == null) {
                    new Menus.ClassMenu(core, p).open();
                }
            }, 40L);
        }
    }

    // ------------------------------------------------------------------ cabut / pulihkan

    public Result revoke(Profile pr, Player online) {
        if (pr.status == GuildStatus.REVOKED) {
            return Result.ALREADY;
        }
        if (pr.status != GuildStatus.REGISTERED) {
            return Result.NOT_REGISTERED;
        }
        pr.status = GuildStatus.REVOKED;
        core.save(pr);
        if (online != null) {
            dialogue.end(online.getUniqueId());
            online.closeInventory();
            core.applyBuffs(online);
            core.syncRank(online, "");
            online.sendMessage(Text.c(Text.PREFIX + msgs.get("revoked-notice")));
        }
        return Result.OK;
    }

    public Result restore(Profile pr, Player online) {
        if (pr.status != GuildStatus.REVOKED) {
            return Result.NOT_REVOKED;
        }
        pr.status = GuildStatus.REGISTERED;
        pr.restoredNotice = true;
        if (pr.level < 1) {
            pr.level = 1;
        }
        core.save(pr);
        if (online != null) {
            core.checkDay(pr);
            core.applyBuffs(online);
            core.syncRank(online, "");
            online.sendMessage(Text.c(Text.PREFIX + msgs.get("restored-notice")));
        }
        return Result.OK;
    }

    // ------------------------------------------------------------------ rank (admin)

    public Result setRank(Profile pr, String key, Player online) {
        int idx = core.rankIndex(key);
        if (idx < 0 || key.equalsIgnoreCase("SS") || key.equalsIgnoreCase("SSS")) {
            return Result.UNKNOWN_RANK;
        }
        if (pr.status == GuildStatus.UNREGISTERED) {
            return Result.NOT_REGISTERED;
        }
        String old = core.rank(pr.rank) == null ? "" : core.rank(pr.rank).key();
        core.applyRank(pr, idx, "ADMIN");
        core.save(pr);
        if (online != null) {
            core.syncRank(online, old);
            core.tracker.rankChanged(online);
        }
        return Result.OK;
    }

    /** Reset aman: kembali ke F. Level, EXP, quest, GP, class tidak disentuh. */
    public Result resetRank(Profile pr, Player online) {
        if (pr.status == GuildStatus.UNREGISTERED) {
            return Result.NOT_REGISTERED;
        }
        return setRank(pr, "F", online);
    }

    // ------------------------------------------------------------------ tampilan

    /** Daftar rank F-S berwarna rarity. Jika pr != null, tandai rank saat ini. */
    public List<String> rankLadderLore(Profile pr) {
        List<String> out = new ArrayList<>();
        for (Defs.Rank r : core.ranks) {
            String mark = "&8• ";
            if (pr != null && pr.member()) {
                mark = r.index() < pr.rank ? "&a✔ " : (r.index() == pr.rank ? "&e▶ " : "&8• ");
            }
            out.add(mark + r.label() + " &8(" + (r.rarity().isEmpty() ? "-" : r.rarity()) + ")");
        }
        return out;
    }

    /** Teks daftar rank untuk dialog (satu baris per rank). */
    public List<String> rankLines() {
        List<String> out = new ArrayList<>();
        for (Defs.Rank r : core.ranks) {
            StringBuilder sb = new StringBuilder("&7- ").append(r.tag()).append(" &8| &7").append(r.rarity());
            if (r.minLevel() > 0) {
                sb.append(" &8| &7Lv ").append(r.minLevel());
            }
            out.add(sb.toString());
        }
        return out;
    }
}
