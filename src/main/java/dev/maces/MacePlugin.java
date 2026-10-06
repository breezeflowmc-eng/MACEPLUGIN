package dev.maces;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MacePlugin extends JavaPlugin {

    private MaceManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new MaceManager(this);
        getServer().getPluginManager().registerEvents(new MaceListener(this, manager), this);

        // Ambient particles while holding a mace
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                MaceTier t = manager.tierOf(p.getInventory().getItemInMainHand());
                if (t != null) manager.trail(p, t);
            }
        }, 10L, 3L);

        // Make sure players already online get a mace after a /reload
        for (Player p : Bukkit.getOnlinePlayers()) manager.giveOrUpdate(p);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            int kills = manager.getKills(p);
            MaceTier t = MaceTier.fromKills(kills);
            MaceTier next = t.next();
            p.sendMessage(Txt.c("&6Kills: " + kills + " | Mace: " + t.displayName
                    + (next != null ? " | Next: " + next.displayName + " at " + next.kills : " | MAX")));
            return true;
        }

        if (args[0].equalsIgnoreCase("give") && sender instanceof Player p) {
            manager.giveOrUpdate(p);
            return true;
        }

        if (args[0].equalsIgnoreCase("set")) {
            if (!sender.hasPermission("mace.admin")) {
                sender.sendMessage(Txt.c("&cNo permission."));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Txt.c("&cUsage: /mace set <player> <kills>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Txt.c("&cPlayer not found."));
                return true;
            }
            try {
                manager.setKills(target, Integer.parseInt(args[2]));
                sender.sendMessage(Txt.c("&aSet " + target.getName() + " to " + args[2] + " kills."));
            } catch (NumberFormatException ex) {
                sender.sendMessage(Txt.c("&cKills must be a number."));
            }
            return true;
        }
        return false;
    }
}
