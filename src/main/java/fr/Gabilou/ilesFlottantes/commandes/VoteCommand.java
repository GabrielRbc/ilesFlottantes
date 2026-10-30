package fr.Gabilou.ilesFlottantes.commandes;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.Gabilou.ilesFlottantes.modeles.results.FinalResults;
import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import fr.Gabilou.ilesFlottantes.utils.MessageManager;
import io.papermc.paper.command.brigadier.*;
import org.bukkit.entity.Player;
import java.util.Locale;

/** Vote public unique par UUID ; les suggestions ne contiennent que les équipes éligibles. */
public final class VoteCommand {
    private final FinalResults results;
    private final TeamManager teams;
    private final MessageManager messages;

    public VoteCommand(FinalResults results, TeamManager teams, MessageManager messages) {
        this.results = results; this.teams = teams; this.messages = messages;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("vote").executes(ctx -> {
            ctx.getSource().getSender().sendMessage(messages.get("results.vote-usage")); return 1;
        }).then(Commands.argument("equipe", StringArgumentType.greedyString()).suggests((ctx, builder) -> {
            if (ctx.getSource().getSender() instanceof Player player) {
                results.candidates(player.getUniqueId()).stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                        .forEach(name -> builder.suggest(name, MessageComponentSerializer.message().serialize(CommandSupport.teamLabel(teams.getTeamByName(name)))));
            }
            return builder.buildFuture();
        }).executes(ctx -> {
            var sender = ctx.getSource().getSender();
            if (!(sender instanceof Player player)) { sender.sendMessage(messages.get("general.only-players")); return 0; }
            String name = StringArgumentType.getString(ctx, "equipe");
            var team = teams.getTeamByName(name);
            var result = results.vote(player.getUniqueId(), team == null ? name : team.getName());
            sender.sendMessage(messages.get("results.vote-results." + result.name(), team == null ? name : team.getName()));
            return result == FinalResults.VoteResult.ACCEPTED ? 1 : 0;
        })).build();
    }
}
