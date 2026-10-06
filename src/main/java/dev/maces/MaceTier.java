package dev.maces;

import org.bukkit.Color;
import org.bukkit.Particle;

public enum MaceTier {
    //        name               gradient (MiniMessage stops)       kills dmg dens brch wind accent particle
    BRONZE   ("Bronze Mace",     "#e0954a:#8a5522",                  0,   1,  0,   0,   0,  null),
    IRON     ("Iron Mace",       "#f2f2f2:#8d9aa6",                  4,   3,  1,   0,   0,  Particle.CRIT),
    GOLD     ("Golden Mace",     "#ffe14d:#ff9d00",                  9,   5,  2,   0,   1,  Particle.WAX_ON),
    DIAMOND  ("Diamond Mace",    "#7df9ff:#1fb5d6",                 15,   7,  3,   1,   1,  Particle.GLOW),
    NETHERITE("Netherite Mace",  "#a78a95:#ff5a1f",                 22,  10,  4,   2,   2,  Particle.SOUL_FIRE_FLAME),
    DRAGON   ("Dragon Mace",     "#d8a4ff:#6a00f4",                 30,  14,  5,   3,   3,  Particle.PORTAL),
    GOD      ("GOD MACE",        "#fff176:#ff4fd8:#4df3ff",         40,  20,  5,   4,   3,  Particle.END_ROD);

    public final String displayName;
    public final String gradient;
    public final int kills;
    public final int bonusDamage; // added on top of the player's base 1 damage
    public final int density;
    public final int breach;
    public final int windBurst;
    public final Particle accent;

    MaceTier(String displayName, String gradient, int kills, int bonusDamage,
             int density, int breach, int windBurst, Particle accent) {
        this.displayName = displayName;
        this.gradient = gradient;
        this.kills = kills;
        this.bonusDamage = bonusDamage;
        this.density = density;
        this.breach = breach;
        this.windBurst = windBurst;
        this.accent = accent;
    }

    public Color color() {
        String first = gradient.split(":")[0].substring(1);
        return Color.fromRGB(Integer.parseInt(first, 16));
    }

    public MaceTier next() {
        MaceTier[] v = values();
        return ordinal() + 1 < v.length ? v[ordinal() + 1] : null;
    }

    public static MaceTier fromKills(int kills) {
        MaceTier result = BRONZE;
        for (MaceTier t : values()) {
            if (kills >= t.kills) result = t;
        }
        return result;
    }
}
