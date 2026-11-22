package my.nereus.relics;

import my.nereus.relics.altar.AltarManager;
import my.nereus.relics.altar.Effects;
import my.nereus.relics.command.AltarCommand;
import my.nereus.relics.delivery.DeliveryManager;
import my.nereus.relics.hologram.HologramManager;
import my.nereus.relics.listener.AltarListener;
import my.nereus.relics.offering.OfferingManager;
import my.nereus.relics.protection.AltarProtection;
import my.nereus.relics.protection.ClearLagProtection;
import my.nereus.relics.protection.ExploitProtection;
import my.nereus.relics.ritual.CinematicManager;
import my.nereus.relics.ritual.RitualManager;
import my.nereus.relics.storage.AltarRepository;
import my.nereus.relics.storage.DatabaseManager;
import my.nereus.relics.util.Keys;
import my.nereus.relics.util.Text;
import my.nereus.relics.weapon.WeaponDisplayManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NereusRelics extends JavaPlugin {

    private Keys keys;
    private DatabaseManager db;
    private AltarRepository repo;
    private Effects effects;
    private AltarManager altars;
    private WeaponDisplayManager weapons;
    private HologramManager holograms;
    private OfferingManager offerings;
    private RitualManager rituals;
    private CinematicManager cinematic;
    private DeliveryManager deliveries;
    private ClearLagProtection clearLag;
    private final Map<UUID, String> names = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();
        keys = new Keys(this);
        effects = new Effects();
        try {
            db = new DatabaseManager(new File(getDataFolder(), "relics.db"), getLogger());
            repo = new AltarRepository(db, getLogger());
            repo.createSchema();
            names.putAll(repo.loadNames());

            altars = new AltarManager(this);
            weapons = new WeaponDisplayManager(this);
            holograms = new HologramManager(this);
            offerings = new OfferingManager(this);
            rituals = new RitualManager(this);
            cinematic = new CinematicManager(this);
            deliveries = new DeliveryManager(this);
            clearLag = new ClearLagProtection(this);

            altars.load(repo.loadAll());
            deliveries.load(repo.loadPendingDeliveries());
        } catch (Exception e) {
            getLogger().severe("Gagal menyiapkan database, plugin dimatikan: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Bukkit.getPluginManager().registerEvents(new AltarListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AltarProtection(this), this);
        Bukkit.getPluginManager().registerEvents(new ExploitProtection(this), this);
        Bukkit.getPluginManager().registerEvents(clearLag, this);

        PluginCommand cmd = getCommand("altar");
        if (cmd != null) {
            AltarCommand ac = new AltarCommand(this);
            cmd.setExecutor(ac);
            cmd.setTabCompleter(ac);
        }

        // Dunia & entity sudah siap satu tick setelah enable.
        Bukkit.getScheduler().runTask(this, () -> {
            altars.startup();
            for (Player p : Bukkit.getOnlinePlayers()) {
                rememberName(p.getUniqueId(), p.getName());
                deliveries.process(p);
            }
        });
    }

    @Override
    public void onDisable() {
        if (rituals != null) rituals.shutdown();
        if (cinematic != null) cinematic.shutdown();
        if (altars != null) altars.shutdown();
        Bukkit.getScheduler().cancelTasks(this);
        if (db != null) db.close();
    }

    public void reloadAll() {
        reloadConfig();
    }

    // ---------------------------------------------------------------- akses

    public Keys keys() { return keys; }
    public AltarRepository repo() { return repo; }
    public Effects effects() { return effects; }
    public AltarManager altars() { return altars; }
    public WeaponDisplayManager weapons() { return weapons; }
    public HologramManager holograms() { return holograms; }
    public OfferingManager offerings() { return offerings; }
    public RitualManager rituals() { return rituals; }
    public CinematicManager cinematic() { return cinematic; }
    public DeliveryManager deliveries() { return deliveries; }
    public ClearLagProtection clearLag() { return clearLag; }

    // ---------------------------------------------------------------- nama pemain

    public void rememberName(UUID id, String name) {
        String old = names.put(id, name);
        if (!name.equals(old) && repo != null) repo.saveNameAsync(id, name);
    }

    public String nameOf(UUID id) {
        String n = names.get(id);
        if (n != null) return n;
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        String off = Bukkit.getOfflinePlayer(id).getName();
        return off != null ? off : id.toString().substring(0, 8);
    }

    // ---------------------------------------------------------------- pesan

    /** Pesan dari config dengan placeholder: pasangan kunci, nilai. */
    public String m(String key, String... kv) {
        String s = getConfig().getString("messages." + key, "");
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        return getConfig().getString("messages.prefix", "") + s;
    }

    public void tell(CommandSender to, String key, String... kv) {
        to.sendMessage(Text.color(m(key, kv)));
    }

    public void tellRaw(CommandSender to, String legacy) {
        to.sendMessage(Text.color(getConfig().getString("messages.prefix", "") + legacy));
    }
}
