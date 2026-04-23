package nl.kmc.kmccore.listeners;

import nl.kmc.kmccore.KMCCore;
import nl.kmc.kmccore.models.PlayerData;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

/**
 * Handles player join and quit events.
 *
 * <p>On join:
 * <ul>
 *   <li>Loads or creates PlayerData</li>
 *   <li>Applies nametag scoreboard (team colour)</li>
 *   <li>Sets up personal sidebar scoreboard</li>
 *   <li>Updates the tab-list header/footer</li>
 * </ul>
 *
 * <p>On quit:
 * <ul>
 *   <li>Saves and unloads player data from cache</li>
 *   <li>Cleans up their scoreboard entry</li>
 * </ul>
 */
public class PlayerJoinQuitListener implements Listener {

    private final KMCCore plugin;

    public PlayerJoinQuitListener(KMCCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        // Load / create persistent data
        PlayerData pd = plugin.getPlayerDataManager()
                .getOrCreate(player.getUniqueId(), player.getName());

        // Apply team nametag colours + tab name colour
        plugin.getTabListManager().applyTeamNametag(player);

        // Set up sidebar scoreboard
        plugin.getScoreboardManager().onPlayerJoin(player);

        // Add to automation bossbar if running
        plugin.getAutomationManager().addPlayerToBossBar(player);

        // Tab-list header / footer
        plugin.getTabListManager().updateTabList(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        plugin.getPlayerDataManager().unload(player.getUniqueId());
        plugin.getScoreboardManager().onPlayerQuit(player);
    }

}
