package com.nereus.rpg;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pelacak quest berbasis event (tanpa scheduler). Setiap kejadian dihitung sekali, memakai validasi anti-eksploit:
 * blok buatan pemain tidak dihitung untuk BREAK, tanam/pasang di posisi yang baru dihancurkan tidak dihitung,
 * craft shift-click dihitung dari jumlah yang benar-benar jadi, craft bolak-balik (blok/ingot) tidak dihitung.
 */
public final class QuestEvents implements Listener {
    private static final Set<Material> CROPS = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH,
            Material.TORCHFLOWER_CROP, Material.PITCHER_CROP);
    private static final Set<Material> REVERSIBLE = Set.of(Material.DIAMOND, Material.EMERALD, Material.LAPIS_LAZULI,
            Material.REDSTONE, Material.COAL, Material.WHEAT, Material.BONE_MEAL, Material.SLIME_BALL,
            Material.HONEYCOMB, Material.MELON_SLICE);

    private final NereusRPG plugin;
    private final Core core;
    private final Map<Long, Long> recentBreak = new HashMap<>();
    private final Map<UUID, Long> lastSleep = new HashMap<>();

    public QuestEvents(NereusRPG plugin, Core core) {
        this.plugin = plugin;
        this.core = core;
        // Hook penjualan EconomyShopGUI (opsional, lewat refleksi). Dicoba setelah semua plugin termuat.
        Bukkit.getScheduler().runTaskLater(plugin, this::hookShop, 40L);
    }

    private static long key(Block b) {
        return b.getBlockKey() * 31L + b.getWorld().getUID().hashCode();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastSleep.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ blok

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();
        if (recentBreak.size() > 3000) {
            recentBreak.clear();
        }
        recentBreak.put(key(b), System.currentTimeMillis());
        boolean byPlayer = core.placed.remove(b);
        Material m = b.getType();
        if (CROPS.contains(m) && b.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge()) {
            core.tracker.progress(p, Objective.Type.HARVEST, m.name());
        }
        if (!byPlayer) {
            core.tracker.progress(p, Objective.Type.BREAK, m.name());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent e) {
        core.tracker.progress(e.getPlayer(), Objective.Type.HARVEST, e.getHarvestedBlock().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        Long t = recentBreak.get(key(b));
        if (t != null && System.currentTimeMillis() - t < 30_000L) {
            return; // pasang ulang di posisi yang baru dihancurkan tidak dihitung
        }
        Material m = b.getType();
        Player p = e.getPlayer();
        if (CROPS.contains(m) || m.name().endsWith("_SAPLING") || m == Material.MANGROVE_PROPAGULE) {
            core.tracker.progress(p, Objective.Type.PLANT, m.name());
        }
        core.tracker.progress(p, Objective.Type.PLACE, m.name());
    }

    // ------------------------------------------------------------ mob

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim instanceof Player || victim instanceof ArmorStand) {
            return;
        }
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }
        boolean bow = false;
        boolean melee = false;
        EntityDamageEvent last = victim.getLastDamageCause();
        if (last instanceof EntityDamageByEntityEvent ede) {
            Entity d = ede.getDamager();
            bow = d instanceof AbstractArrow && !(d instanceof Trident);
            melee = d instanceof Player;
        }
        boolean adult = !(victim instanceof org.bukkit.entity.Ageable ag) || ag.isAdult();
        core.tracker.kill(killer, victim.getType().name(), Tracker.Ctx.env(killer).weapon(bow, melee).adult(adult));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (e.getBreeder() instanceof Player p) {
            core.tracker.progress(p, Objective.Type.BREED, e.getMother().getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        if (e.getOwner() instanceof Player p) {
            core.tracker.progress(p, Objective.Type.TAME, e.getEntity().getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent e) {
        core.tracker.progress(e.getPlayer(), Objective.Type.SHEAR, e.getEntity().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMilk(PlayerInteractEntityEvent e) {
        EntityType t = e.getRightClicked().getType();
        if ((t != EntityType.COW && t != EntityType.MOOSHROOM)
                || !(e.getRightClicked() instanceof org.bukkit.entity.Ageable cow) || !cow.isAdult()) {
            return;
        }
        ItemStack held = e.getPlayer().getInventory().getItem(e.getHand());
        if (held != null && held.getType() == Material.BUCKET) {
            core.tracker.progress(e.getPlayer(), Objective.Type.MILK, t.name());
        }
    }

    // ------------------------------------------------------------ crafting dan peleburan

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || e.getRecipe() == null) {
            return;
        }
        ItemStack res = e.getRecipe().getResult();
        Material m = res.getType();
        String n = m.name();
        if (n.endsWith("_BLOCK") || n.endsWith("_INGOT") || n.endsWith("_NUGGET") || REVERSIBLE.contains(m)) {
            return; // bisa dikembalikan lewat crafting: tidak dihitung
        }
        int per = Math.max(1, res.getAmount());
        int times = 1;
        if (e.isShiftClick()) {
            int min = Integer.MAX_VALUE;
            for (ItemStack it : e.getInventory().getMatrix()) {
                if (it != null && !it.getType().isAir()) {
                    min = Math.min(min, it.getAmount());
                }
            }
            times = min == Integer.MAX_VALUE ? 1 : min;
            int room = 0;
            for (ItemStack s : p.getInventory().getStorageContents()) {
                if (s == null || s.getType().isAir()) {
                    room += m.getMaxStackSize();
                } else if (s.isSimilar(res)) {
                    room += Math.max(0, s.getMaxStackSize() - s.getAmount());
                }
            }
            times = Math.min(times, room / per);
        } else {
            ItemStack cur = e.getCursor();
            if (cur != null && !cur.getType().isAir() && (!cur.isSimilar(res) || cur.getAmount() + per > cur.getMaxStackSize())) {
                return;
            }
        }
        if (times > 0) {
            core.tracker.progress(p, Objective.Type.CRAFT, n, per * times, Tracker.Ctx.env(p));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmelt(FurnaceExtractEvent e) {
        if (e.getItemAmount() > 0) {
            core.tracker.progress(e.getPlayer(), Objective.Type.SMELT, e.getItemType().name(), e.getItemAmount(),
                    Tracker.Ctx.env(e.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmith(SmithItemEvent e) {
        ItemStack r = e.getInventory().getResult();
        if (e.getWhoClicked() instanceof Player p && r != null && !r.getType().isAir()) {
            core.tracker.progress(p, Objective.Type.SMITH, r.getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        core.tracker.progress(e.getEnchanter(), Objective.Type.ENCHANT, e.getItem().getType().name());
    }

    // ------------------------------------------------------------ hidup

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        Material m = e.getItem().getType();
        if (m.isEdible()) {
            core.tracker.progress(e.getPlayer(), Objective.Type.EAT, m.name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWake(PlayerBedLeaveEvent e) {
        Player p = e.getPlayer();
        if (!e.getBed().getWorld().isDayTime()) {
            return; // bangun di tengah malam: tidak dihitung
        }
        long now = System.currentTimeMillis();
        Long last = lastSleep.get(p.getUniqueId());
        if (last != null && now - last < 300_000L) {
            return;
        }
        lastSleep.put(p.getUniqueId(), now);
        core.tracker.progress(p, Objective.Type.SLEEP, "");
    }

    // ------------------------------------------------------------ EconomyShopGUI (opsional, refleksi)

    private void hookShop() {
        ClassLoader loader = null;
        for (String name : new String[]{"EconomyShopGUI", "EconomyShopGUI-Premium"}) {
            Plugin p = Bukkit.getPluginManager().getPlugin(name);
            if (p != null && p.isEnabled()) {
                loader = p.getClass().getClassLoader();
                break;
            }
        }
        if (loader == null) {
            plugin.getLogger().info("Quest Economy: EconomyShopGUI tidak ditemukan, quest SELL/BUY tidak akan maju.");
            return;
        }
        try {
            Class<?> raw = Class.forName("me.gypopo.economyshopgui.api.events.PostTransactionEvent", true, loader);
            @SuppressWarnings("unchecked")
            Class<? extends Event> type = (Class<? extends Event>) raw.asSubclass(Event.class);
            Bukkit.getPluginManager().registerEvent(type, new Listener() {
            }, EventPriority.MONITOR, (l, ev) -> {
                if (type.isInstance(ev)) {
                    try {
                        onShop(ev);
                    } catch (Throwable t) {
                        plugin.getLogger().warning("Quest Economy hook error: " + t);
                    }
                }
            }, plugin, true);
            plugin.getLogger().info("Quest Economy: terhubung ke EconomyShopGUI (PostTransactionEvent).");
        } catch (Throwable t) {
            plugin.getLogger().warning("Quest Economy: gagal terhubung ke EconomyShopGUI: " + t);
        }
    }

    private static Object call(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Material materialOf(Object shopItem) {
        if (shopItem == null) {
            return null;
        }
        for (String name : new String[]{"getItemToGive", "getItemStack", "getItem", "getMaterial", "getType"}) {
            Object o = call(shopItem, name);
            if (o instanceof ItemStack is) {
                return is.getType();
            }
            if (o instanceof Material mm) {
                return mm;
            }
        }
        return null;
    }

    private void onShop(Event ev) {
        Object type = call(ev, "getTransactionType");
        String ty = type == null ? "" : type.toString().toUpperCase(Locale.ROOT);
        boolean sell = ty.contains("SELL");
        boolean buy = ty.contains("BUY");
        if (!(call(ev, "getPlayer") instanceof Player p) || (!sell && !buy)) {
            return;
        }
        Object result = call(ev, "getTransactionResult");
        if (result != null && !result.toString().toUpperCase(Locale.ROOT).contains("SUCCESS")) {
            return;
        }
        double total = 0;
        Map<?, ?> items = call(ev, "getItems") instanceof Map<?, ?> m ? m : null;
        Map<?, ?> prices = call(ev, "getPrices") instanceof Map<?, ?> m2 ? m2 : null;
        if (items != null && !items.isEmpty() && prices != null && !prices.isEmpty()) {
            Object key = null;
            for (Object k : prices.keySet()) {
                if (key == null || String.valueOf(k).toUpperCase(Locale.ROOT).contains("VAULT")) {
                    key = k;
                }
            }
            if (key != null && prices.get(key) instanceof Number n) {
                total = n.doubleValue();
            }
            if (sell) {
                for (Map.Entry<?, ?> en : items.entrySet()) {
                    Material mat = materialOf(en.getKey());
                    if (mat != null && en.getValue() instanceof Number q && q.intValue() > 0) {
                        core.tracker.progress(p, Objective.Type.SELL, mat.name(), q.intValue(), Tracker.Ctx.env(p));
                    }
                }
            }
        } else {
            if (call(ev, "getPrice") instanceof Number n) {
                total = n.doubleValue();
            }
            Material mat = materialOf(call(ev, "getShopItem"));
            int qty = call(ev, "getAmount") instanceof Number q ? Math.max(1, q.intValue()) : 1;
            if (sell && mat != null) {
                core.tracker.progress(p, Objective.Type.SELL, mat.name(), qty, Tracker.Ctx.env(p));
            }
        }
        int nr = (int) Math.floor(total);
        if (nr > 0) {
            core.tracker.progress(p, sell ? Objective.Type.SELL_VALUE : Objective.Type.BUY_VALUE, "", nr, Tracker.Ctx.env(p));
        }
    }
}
