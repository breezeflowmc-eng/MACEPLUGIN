package dev.maces;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class MaceManager {

    private static final double BASE_ATTACK_SPEED_MOD = -3.4; // vanilla mace

    private final MacePlugin plugin;

    private final NamespacedKey tierKey;
    private final NamespacedKey killsKey;
    private final NamespacedKey dmgKey;
    private final NamespacedKey spdKey;

    public MaceManager(MacePlugin plugin) {
        this.plugin = plugin;
        this.tierKey = new NamespacedKey(plugin, "mace_tier");
        this.killsKey = new NamespacedKey(plugin, "kills");
        this.dmgKey = new NamespacedKey(plugin, "mace_damage");
        this.spdKey = new NamespacedKey(plugin, "mace_speed");
    }

    // ---------------------------------------------------------------- kills

    public int getKills(Player p) {
        return p.getPersistentDataContainer().getOrDefault(killsKey, PersistentDataType.INTEGER, 0);
    }

    public void resetKills(Player p) {
        p.getPersistentDataContainer().remove(killsKey);
    }

    public void setKills(Player p, int kills) {
        MaceTier before = MaceTier.fromKills(getKills(p));
        p.getPersistentDataContainer().set(killsKey, PersistentDataType.INTEGER, Math.max(0, kills));
        MaceTier after = MaceTier.fromKills(getKills(p));
        giveOrUpdate(p);
        if (after.ordinal() > before.ordinal()) levelUp(p, after);
    }

    public void addKill(Player p) {
        setKills(p, getKills(p) + 1);
    }

    // ---------------------------------------------------------------- items

    public boolean isMace(ItemStack item) {
        return tierOf(item) != null;
    }

    public MaceTier tierOf(ItemStack item) {
        if (item == null || item.getType() != Material.MACE || !item.hasItemMeta()) return null;
        String s = item.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return MaceTier.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public ItemStack create(MaceTier tier, int kills) {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("\u00a7r" + Txt.gradient(tier.displayName, tier.gradient, true));

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(Txt.c("&r&7Attack Damage: &f" + (1 + tier.bonusDamage)));
        if (tier == MaceTier.GOD) {
            lore.add("\u00a7r" + Txt.gradient("\u2620 Instantly kills anything you hit", "#ff4fd8:#ff3b3b", false));
            lore.add("\u00a7r" + Txt.gradient("\u26A1 Right-click to Lunge", "#4df3ff:#7df9ff", false));
        }
        lore.add("");
        lore.add(Txt.c("&r&7Kills: &f" + kills));
        MaceTier next = tier.next();
        if (next != null) {
            lore.add("\u00a7r\u00a77Next: " + Txt.gradient(next.displayName, next.gradient, false)
                    + Txt.c(" &8(" + (next.kills - kills) + " more kills)"));
        } else {
            lore.add("\u00a7r" + Txt.gradient("Maximum power reached", tier.gradient, false));
        }
        lore.add("");
        lore.add(Txt.c("&r&8Soulbound"));
        meta.setLore(lore);

        // Stats
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                dmgKey, tier.bonusDamage, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                spdKey, BASE_ATTACK_SPEED_MOD + tier.ordinal() * 0.12,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

        // Enchants
        if (tier.density > 0) meta.addEnchant(Enchantment.DENSITY, tier.density, true);
        if (tier.breach > 0) meta.addEnchant(Enchantment.BREACH, tier.breach, true);
        if (tier.windBurst > 0) meta.addEnchant(Enchantment.WIND_BURST, tier.windBurst, true);
        if (tier == MaceTier.GOD) {
            try {
                @SuppressWarnings("deprecation")
                Enchantment lunge = Enchantment.getByKey(NamespacedKey.minecraft("lunge"));
                if (lunge != null) meta.addEnchant(lunge, 3, true);
            } catch (Throwable ignored) {
                // Lunge enchant not available on this version - the right-click dash still works.
            }
        }

        // Custom model for the resource pack (config: custom-models)
        if (plugin.getConfig().getBoolean("custom-models", true)) {
            NamespacedKey model = NamespacedKey.fromString("maceprogression:" + tier.name().toLowerCase());
            if (model != null) meta.setItemModel(model);
        }

        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.name());

        item.setItemMeta(meta);
        return item;
    }

    /** Replaces the player's mace with the correct one for their kills (or gives one if missing). */
    public void giveOrUpdate(Player p) {
        int kills = getKills(p);
        ItemStack fresh = create(MaceTier.fromKills(kills), kills);
        PlayerInventory inv = p.getInventory();
        boolean replaced = false;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (isMace(it)) {
                if (!replaced) {
                    inv.setItem(i, fresh);
                    replaced = true;
                } else {
                    inv.setItem(i, null);
                }
            }
        }
        if (!replaced) {
            inv.addItem(fresh).values().forEach(left ->
                    p.getWorld().dropItemNaturally(p.getLocation(), left));
        }
    }

    // -------------------------------------------------------------- effects

    private void levelUp(Player p, MaceTier tier) {
        p.sendTitle("\u00a7r" + Txt.gradient(tier.displayName, tier.gradient, true),
                Txt.c("&7Your mace has evolved!"), 6, 40, 12);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, .5, .8, .5, .3);

        if (tier == MaceTier.GOD) {
            p.getWorld().strikeLightningEffect(p.getLocation());
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
            Bukkit.broadcastMessage(Txt.gradient(p.getName() + " has ascended and now wields the GOD MACE!",
                    tier.gradient, true));
        }
    }

    public void hitEffect(Location loc, MaceTier tier) {
        World w = loc.getWorld();
        w.spawnParticle(Particle.DUST, loc, 25, .3, .4, .3, 0, new Particle.DustOptions(tier.color(), 1.4f));
        if (tier.accent != null) w.spawnParticle(tier.accent, loc, 15, .3, .4, .3, .05);
        w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.7f + tier.ordinal() * 0.1f);
        if (tier == MaceTier.GOD) {
            w.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 30, .4, .6, .4, .3);
            w.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.4f);
        }
    }

    /** Ambient particles around a player holding a mace. */
    public void trail(Player p, MaceTier tier) {
        Vector dir = p.getLocation().getDirection().normalize();
        Location hand = p.getEyeLocation().add(dir.clone().multiply(0.8)).subtract(0, 0.6, 0);
        World w = p.getWorld();
        w.spawnParticle(Particle.DUST, hand, 2, .12, .12, .12, 0, new Particle.DustOptions(tier.color(), 1.0f));
        if (tier.accent != null) w.spawnParticle(tier.accent, hand, 1, .1, .1, .1, .01);

        if (tier == MaceTier.GOD) {
            double a = p.getTicksLived() * 0.4;
            Location orbit = p.getLocation().add(Math.cos(a) * 0.9, 1.0 + Math.sin(a * 0.5) * 0.5, Math.sin(a) * 0.9);
            w.spawnParticle(Particle.END_ROD, orbit, 1, 0, 0, 0, 0);
        }
    }
}
