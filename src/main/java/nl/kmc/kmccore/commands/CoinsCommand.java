package nl.kmc.kmccore.commands;

import nl.kmc.kmccore.KMCCore;
import nl.kmc.kmccore.models.PlayerData;
import nl.kmc.kmccore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/** /kmccoins <set|add|remove|get> <player> [amount] */
public class CoinsCommand implements CommandExecutor, TabCompleter {

    private final KMCCore plugin;
    public CoinsCommand(KMCCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("kmc.coins.admin")) { sender.sendMessage(MessageUtil.get("no-permission")); return true; }
        if (args.length < 2) { sender.sendMessage(MessageUtil.get("invalid-usage").replace("{usage}", "/kmccoins <set|add|remove|get> <player> [amount]")); return true; }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { sender.sendMessage(MessageUtil.get("player-not-found").replace("{player}", args[1])); return true; }

        PlayerData pd = plugin.getPlayerDataManager().getOrCreate(target.getUniqueId(), target.getName());

        switch (args[0].toLowerCase()) {
            case "get"    -> sender.sendMessage(MessageUtil.get("coins.get").replace("{player}", target.getName()).replace("{amount}", String.valueOf(pd.getCoins())));
            case "set"    -> { int a = parseInt(sender, args, 2); if (a < 0) return true; pd.setCoins(a); save(pd); sender.sendMessage(MessageUtil.get("coins.set").replace("{player}", target.getName()).replace("{amount}", String.valueOf(a))); }
            case "add"    -> { int a = parseInt(sender, args, 2); if (a < 0) return true; pd.addCoins(a); save(pd); sender.sendMessage(MessageUtil.get("coins.add").replace("{player}", target.getName()).replace("{amount}", String.valueOf(a))); }
            case "remove" -> { int a = parseInt(sender, args, 2); if (a < 0) return true; pd.removeCoins(a); save(pd); sender.sendMessage(MessageUtil.get("coins.remove").replace("{player}", target.getName()).replace("{amount}", String.valueOf(a))); }
            default       -> sender.sendMessage(MessageUtil.get("invalid-usage").replace("{usage}", "/kmccoins <set|add|remove|get> <player> [amount]"));
        }
        return true;
    }

    private int parseInt(CommandSender s, String[] args, int idx) {
        if (args.length <= idx) { s.sendMessage(MessageUtil.get("invalid-number")); return -1; }
        try { return Integer.parseInt(args[idx]); } catch (NumberFormatException e) { s.sendMessage(MessageUtil.get("invalid-number")); return -1; }
    }

    private void save(PlayerData pd) { plugin.getDatabaseManager().savePlayer(pd); }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] args) {
        if (args.length == 1) return List.of("set","add","remove","get").stream().filter(o -> o.startsWith(args[0])).collect(Collectors.toList());
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        return List.of();
    }
}
