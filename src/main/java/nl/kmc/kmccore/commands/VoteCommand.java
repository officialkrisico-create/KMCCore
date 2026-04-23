package nl.kmc.kmccore.commands;

import nl.kmc.kmccore.KMCCore;
import nl.kmc.kmccore.util.MessageUtil;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

/**
 * /kmcvote <1|2|3>
 *
 * <p>Registered as a real command so the clickable chat buttons can use
 * {@code ClickEvent.runCommand("/kmcvote 1")} instead of chat input.
 * Players can also type it manually.
 */
public class VoteCommand implements CommandExecutor {

    private final KMCCore plugin;

    public VoteCommand(KMCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can vote.");
            return true;
        }

        if (!plugin.getGameManager().isVotingActive()) {
            player.sendMessage(MessageUtil.get("vote.not-active"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(MessageUtil.get("invalid-usage").replace("{usage}", "/kmcvote <1|2|3>"));
            return true;
        }

        int option;
        try {
            option = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtil.get("invalid-number"));
            return true;
        }

        plugin.getGameManager().castVote(player, option);
        return true;
    }
}
