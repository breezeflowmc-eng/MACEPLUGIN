package dev.maces;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class MaceListener implements Listener {

    private final MacePlugin plugin;
    private final MaceManager manager;
    private boolean busy = false; // guards against recursion while instakilling

    public MaceListener(MacePlugin plugin, MaceManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ---------------------------------------------------- give / soulbound

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        manager.giveOrUpdate(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> manager.giveOrUpdate(p));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(manager::isMace);
        if (plugin.getConfig().getBoolean("reset-kills-on-death", false)) {
            Player p = e.getEntity();
            manager.resetKills(p);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (manager.isMace(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;

        boolean block = false;
        if (clicked.getType() != InventoryType.PLAYER) {
            if (manager.isMace(e.getCursor())) block = true;
            if (e.getClick() == ClickType.NUMBER_KEY
                    && manager.isMace(p.getInventory().getItem(e.getHotbarButton()))) block = true;
            if (e.getClick() == ClickType.SWAP_OFFHAND
                    && manager.isMace(p.getInventory().getItemInOffHand())) block = true;
        } else if (e.isShiftClick() && manager.isMace(e.getCurrentItem())
                && e.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
            block = true;
        }
        if (block) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!manager.isMace(e.getOldCursor())) return;
        int top = e.getView().getTopInventory().getSize();
        for (int slot : e.getRawSlots()) {
            if (slot < top) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // ------------------------------------------------------------- kills

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || killer == dead) return;
        if (!(dead instanceof Player) && !plugin.getConfig().getBoolean("count-mobs", false)) return;
        manager.addKill(killer);
    }

    // ------------------------------------------------------------ combat

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (busy) return;
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity victim)) return;

        MaceTier tier = manager.tierOf(p.getInventory().getItemInMainHand());
        if (tier == null) return;

        manager.hitEffect(victim.getLocation().add(0, victim.getHeight() / 2, 0), tier);

        if (tier != MaceTier.GOD) return;
        if (victim.isInvulnerable()) return;
        if (victim instanceof Player vp
                && (vp.getGameMode() == GameMode.CREATIVE || vp.getGameMode() == GameMode.SPECTATOR)) return;

        // God Mace: instant kill
        e.setCancelled(true);
        busy = true;
        try {
            victim.damage(1_000_000, p);
            if (!victim.isDead() && victim.getHealth() > 0) { // i-frames or a totem saved them
                victim.setKiller(p);
                victim.setHealth(0);
            }
        } finally {
            busy = false;
        }
    }

    // ------------------------------------------------------------- lunge

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (manager.tierOf(item) != MaceTier.GOD) return;
        if (p.hasCooldown(item)) return;

        p.setCooldown(item, 30);

        Vector dir = p.getLocation().getDirection().normalize().multiply(1.6);
        if (p.isOnGround()) dir.setY(Math.max(dir.getY(), 0.25));
        p.setVelocity(dir);

        var w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1.2f);
        w.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.5, 0), 20, .3, .2, .3, .05);
        w.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 20, .4, .4, .4, .1);
    }
}
