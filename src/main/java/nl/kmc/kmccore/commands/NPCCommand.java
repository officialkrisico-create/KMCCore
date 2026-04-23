package nl.kmc.kmccore.commands;

import nl.kmc.kmccore.KMCCore;
import nl.kmc.kmccore.npc.NPCManager;
import nl.kmc.kmccore.util.MessageUtil;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

/** /kmcnpc <create|remove|list> [type] */
public class NPCCommand implements CommandExecutor {

    private final KMCCore plugin;
    public NPCCommand(KMCCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("kmc.npc.admin")) { sender.sendMessage(MessageUtil.get("no-permission")); return true; }
        if (args.length == 0) { usage(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Only players can create NPCs."); return true; }
                if (args.length < 2) { usage(sender); return true; }
                NPCManager.NpcType type = NPCManager.NpcType.fromString(args[1]);
                if (type == null) { sender.sendMessage(MessageUtil.get("npc.invalid-type")); return true; }
                plugin.getNpcManager().createNpc(type, player.getLocation());
                sender.sendMessage(MessageUtil.get("npc.created").replace("{type}", args[1]));
            }
            case "remove" -> {
                if (args.length < 2) { usage(sender); return true; }
                if (!plugin.getNpcManager().removeNpc(args[1]))
                    sender.sendMessage(MessageUtil.get("npc.not-found"));
                else
                    sender.sendMessage(MessageUtil.get("npc.removed"));
            }
            case "list" -> {
                sender.sendMessage(MessageUtil.get("npc.list-header"));
                for (NPCManager.KmcNpc n : plugin.getNpcManager().getAllNpcs()) {
                    String loc = n.location.getBlockX() + "," + n.location.getBlockY() + "," + n.location.getBlockZ();
                    sender.sendMessage(MessageUtil.get("npc.list-entry")
                            .replace("{id}", n.id)
                            .replace("{type}", n.type.name().toLowerCase())
                            .replace("{location}", loc));
                }
            }
            default -> usage(sender);
        }
        return true;
    }

    private void usage(CommandSender s) { s.sendMessage(MessageUtil.get("invalid-usage").replace("{usage}", "/kmcnpc <create|remove|list> [type]")); }
}
