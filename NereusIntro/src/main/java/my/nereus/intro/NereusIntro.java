package my.nereus.intro;

import my.nereus.intro.command.IntroCommand;
import my.nereus.intro.config.IntroConfig;
import my.nereus.intro.listener.ProtectionListener;
import my.nereus.intro.listener.SessionListener;
import my.nereus.intro.manager.IntroService;
import my.nereus.intro.manager.IsolationService;
import my.nereus.intro.manager.PlatformManager;
import my.nereus.intro.manager.ResourcePackService;
import my.nereus.intro.manager.ProgressManager;
import my.nereus.intro.npc.NpcService;
import my.nereus.intro.recovery.RecoveryService;
import my.nereus.intro.recovery.RecoveryStore;
import my.nereus.intro.session.EndReason;
import my.nereus.intro.session.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class NereusIntro extends JavaPlugin {

    private volatile IntroConfig config;
    private SessionManager sessions;
    private ProgressManager progress;
    private NpcService npcService;
    private RecoveryService recovery;
    private IsolationService isolation;
    private PlatformManager platform;
    private IntroService intro;
    private ResourcePackService resourcePacks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.platform = new PlatformManager();
        this.sessions = new SessionManager();
        this.progress = new ProgressManager(getDataFolder(), getLogger());
        this.npcService = new NpcService(this);
        this.isolation = new IsolationService(this);
        this.recovery = new RecoveryService(this, new RecoveryStore(getDataFolder(), getLogger()));
        this.intro = new IntroService(this);
        this.resourcePacks = new ResourcePackService(this);
        reloadAll();

        IntroCommand executor = new IntroCommand(this);
        for (String name : new String[]{"intro", "nereusintro"}) {
            PluginCommand pc = getCommand(name);
            if (pc == null) {
                getLogger().severe("Command '" + name + "' tidak terdaftar di plugin.yml");
                continue;
            }
            pc.setExecutor(executor);
            pc.setTabCompleter(executor);
        }

        Bukkit.getPluginManager().registerEvents(new SessionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(resourcePacks, this);

        // Setelah semua plugin termuat: pastikan /intro memang milik kita, bukan plugin lain.
        Bukkit.getScheduler().runTask(this, this::checkCommandConflict);

        if (!npcService.isAvailable()) {
            getLogger().severe("FancyNpcs tidak aktif! NPC Nerith tidak akan muncul.");
        }
        getLogger().info("NereusIntro aktif. Floodgate: " + (platform.hasFloodgate() ? "terdeteksi" : "tidak ada")
                + ". Belum diuji di server nyata; lihat README untuk daftar uji Java/Bedrock.");

        // Pemain yang sudah online saat plugin di-load ulang (/reload): pulihkan bila ada file recovery.
        for (var p : Bukkit.getOnlinePlayers()) {
            recovery.recoverIfNeeded(p, () -> { });
        }
    }

    @Override
    public void onDisable() {
        if (sessions != null) sessions.stopAll(EndReason.DISABLE);
        if (npcService != null) npcService.removeAll();
        Bukkit.getScheduler().cancelTasks(this);
    }

    /** Muat ulang config. Sesi aktif memakai snapshot config lamanya sehingga tidak terganggu. */
    public void reloadAll() {
        reloadConfig();
        IntroConfig c = new IntroConfig(getConfig(), getLogger());
        this.config = c;
        npcService.skins().refresh(c);
        List<String> problems = c.locationProblems();
        if (!problems.isEmpty()) {
            getLogger().warning("Intro BELUM bisa berjalan: " + String.join("; ", problems)
                    + ". Atur dengan /intro setpos room dan /intro setpos spawn.");
        }
        if (c.timeline.isEmpty()) getLogger().warning("Timeline cerita kosong; intro tidak bisa berjalan.");
        else getLogger().info("Timeline dimuat: " + c.timeline.size() + " langkah.");
    }

    private void checkCommandConflict() {
        try {
            var registered = Bukkit.getCommandMap().getCommand("intro");
            boolean ours = registered instanceof PluginCommand pc && pc.getPlugin() == this;
            if (!ours) {
                getLogger().warning("Command /intro sudah dipakai plugin lain; NereusIntro TIDAK mengambil alih. "
                        + "Gunakan /nereusintro (alias /nintro) atau /nereusintro:intro.");
            }
        } catch (Throwable t) {
            getLogger().fine("Tidak bisa memeriksa konflik command: " + t);
        }
    }

    public IntroConfig config() { return config; }
    public SessionManager sessions() { return sessions; }
    public ProgressManager progress() { return progress; }
    public NpcService npcService() { return npcService; }
    public RecoveryService recovery() { return recovery; }
    public IsolationService isolation() { return isolation; }
    public PlatformManager platform() { return platform; }
    public IntroService intro() { return intro; }
    public ResourcePackService resourcePacks() { return resourcePacks; }
}
