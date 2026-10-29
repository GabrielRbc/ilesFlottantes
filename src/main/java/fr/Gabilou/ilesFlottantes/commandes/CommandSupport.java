package fr.Gabilou.ilesFlottantes.commandes;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import fr.Gabilou.ilesFlottantes.modeles.teams.TeamModel;
import fr.Gabilou.ilesFlottantes.utils.MessageManager;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Utilitaires communs aux commandes : permission administrative, présentation
 * colorée des équipes et suggestions calculées à partir de l’état courant.
 * <p>Les suggestions de joueurs respectent leur visibilité pour l’émetteur.</p>
 */
final class CommandSupport {
    static final String ADMIN = "ilesflottantes.admin";
    private CommandSupport() { }

    static Component teamLabel(TeamModel team) {
        return Component.text(team.getName(), TextColor.color(team.getColor().asRGB()));
    }

    static CompletableFuture<Suggestions> teams(TeamManager manager, MessageManager messages,
                                                SuggestionsBuilder builder, boolean quoted) {
        for (TeamModel team : manager.getTeams()) {
            String value = quoted && team.getName().contains(" ") ? '\"' + team.getName() + '\"' : team.getName();
            String remaining = builder.getRemainingLowerCase();
            if (value.toLowerCase(Locale.ROOT).startsWith(remaining)
                    || team.getName().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(value, MessageComponentSerializer.message().serialize(
                        Component.text(messages.text("teams.suggestion", team.getName(), team.getPlayers().size()),
                                TextColor.color(team.getColor().asRGB()))));
            }
        }
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> players(CommandSender sender, SuggestionsBuilder builder) {
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> !(sender instanceof Player viewer) || viewer.canSee(player))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                .sorted(String.CASE_INSENSITIVE_ORDER).forEach(builder::suggest);
        return builder.buildFuture();
    }
}
