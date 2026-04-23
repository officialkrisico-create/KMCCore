package nl.kmc.kmccore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import nl.kmc.kmccore.models.KMCGame;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Builds and sends a clickable chat vote message to all online players.
 *
 * <p>Example output:
 * <pre>
 * ┌─────────────────────────────────────┐
 * │   🗳  Stem op de volgende game!     │
 * ├─────────────────────────────────────┤
 * │ [1. Team Skywars] [2. Spleef] [3. Lucky Block] │
 * │  Klik om te stemmen — 30 seconden   │
 * └─────────────────────────────────────┘
 * </pre>
 *
 * Clicking a button runs {@code /kmcvote <number>} for that player.
 */
public final class ClickableVoteMessage {

    private ClickableVoteMessage() {}

    /**
     * Sends the full clickable vote UI to every online player.
     *
     * @param options  the vote options to display (max ~4 fit on one line)
     * @param seconds  how many seconds the vote is open
     */
    public static void send(List<KMCGame> options, int seconds) {
        Component message = buildMessage(options, seconds);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(Component.empty()); // spacing
            p.sendMessage(message);
            p.sendMessage(Component.empty());
        }
    }

    /**
     * Sends a vote-result message to all players showing who won.
     *
     * @param game      the winning game
     * @param voteCount votes it received
     */
    public static void sendResult(KMCGame game, int voteCount) {
        Component msg = Component.text()
                .append(Component.text("✔ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text("Volgende game: ", NamedTextColor.YELLOW))
                .append(Component.text(game.getDisplayName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" (" + voteCount + " stemmen)", NamedTextColor.GRAY))
                .build();

        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
    }

    // ----------------------------------------------------------------
    // Private builders
    // ----------------------------------------------------------------

    private static Component buildMessage(List<KMCGame> options, int seconds) {
        // Colours
        NamedTextColor border  = NamedTextColor.GOLD;
        NamedTextColor heading = NamedTextColor.YELLOW;
        NamedTextColor sub     = NamedTextColor.GRAY;

        String line = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

        TextComponent.Builder builder = Component.text();

        // Top border
        builder.append(Component.text(line, border)).append(Component.newline());

        // Title
        builder.append(Component.text("  🗳  ", NamedTextColor.WHITE))
               .append(Component.text("Stem op de volgende game!", heading, TextDecoration.BOLD))
               .append(Component.newline());

        // Mid border
        builder.append(Component.text(line, border)).append(Component.newline());

        // Vote buttons
        builder.append(Component.text("  ", NamedTextColor.WHITE));
        for (int i = 0; i < options.size(); i++) {
            builder.append(buildButton(i + 1, options.get(i)));
            if (i < options.size() - 1) {
                builder.append(Component.text("  ", NamedTextColor.WHITE));
            }
        }
        builder.append(Component.newline());

        // Footer
        builder.append(Component.text("  Klik om te stemmen  •  " + seconds + " seconden", sub))
               .append(Component.newline());

        // Bottom border
        builder.append(Component.text(line, border));

        return builder.build();
    }

    /**
     * Builds a single clickable button component.
     * Clicking it runs {@code /kmcvote <number>} as the player.
     */
    private static Component buildButton(int number, KMCGame game) {
        String command = "/kmcvote " + number;
        String hoverText = "Klik om te stemmen op\n" + game.getDisplayName();

        return Component.text()
                // Opening bracket
                .append(Component.text("[", NamedTextColor.GOLD, TextDecoration.BOLD))
                // Number
                .append(Component.text(number + ". ", NamedTextColor.WHITE, TextDecoration.BOLD))
                // Game name
                .append(Component.text(game.getDisplayName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                // Closing bracket
                .append(Component.text("]", NamedTextColor.GOLD, TextDecoration.BOLD))
                // Click action
                .clickEvent(ClickEvent.runCommand(command))
                // Hover tooltip
                .hoverEvent(HoverEvent.showText(
                        Component.text(hoverText, NamedTextColor.YELLOW)))
                .build();
    }
}
