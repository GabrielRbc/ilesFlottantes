package fr.Gabilou.ilesFlottantes.commandes;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.Gabilou.ilesFlottantes.modeles.economy.EconomyManager;
import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import fr.Gabilou.ilesFlottantes.modeles.teams.TeamModel;
import fr.Gabilou.ilesFlottantes.utils.MessageManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.stream.Collectors;
import fr.Gabilou.ilesFlottantes.modeles.game.GameSession;

/**
 * Construit les commandes administratives de consultation et d’affectation des équipes.
 * <p>Les effectifs, membres en ligne et soldes affichés sont lus au moment de
 * l’exécution. Les noms d’équipes composés sont acceptés par les arguments finaux.</p>
 */
public final class TeamCommand {
    private final TeamManager teams;
    private final EconomyManager economy;
    private final MessageManager messages;
    private final GameSession game;

    public TeamCommand(TeamManager teams, EconomyManager economy, MessageManager messages, GameSession game) {
        this.teams = teams;
        this.economy = economy;
        this.messages = messages;
        this.game = game;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("team")
                .requires(source -> source.getSender().hasPermission(CommandSupport.ADMIN))
                .executes(context -> help(context.getSource().getSender()))
                .then(Commands.literal("list").executes(context -> list(context.getSource().getSender())))
                .then(Commands.literal("info")
                        .then(Commands.argument("equipe", StringArgumentType.greedyString())
                                .suggests((context, builder) -> CommandSupport.teams(teams, messages, builder, false))
                                .executes(context -> info(context.getSource().getSender(), StringArgumentType.getString(context, "equipe")))))
                .then(Commands.literal("join")
                        .then(Commands.argument("joueur", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSupport.players(context.getSource().getSender(), builder))
                                .then(Commands.argument("equipe", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> CommandSupport.teams(teams, messages, builder, false))
                                        .executes(context -> join(context.getSource().getSender(),
                                                StringArgumentType.getString(context, "joueur"), StringArgumentType.getString(context, "equipe"))))))
                .then(Commands.literal("leave")
                        .then(Commands.argument("joueur", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSupport.players(context.getSource().getSender(), builder))
                                .executes(context -> leave(context.getSource().getSender(), StringArgumentType.getString(context, "joueur")))))
                .build();
    }

    private int help(CommandSender sender) {
        sender.sendMessage(messages.get("teams.usage"));
        return list(sender);
    }

    private int list(CommandSender sender) {
        sender.sendMessage(messages.get("teams.list-title"));
        teams.getTeams().forEach(team -> sender.sendMessage(CommandSupport.teamLabel(team)
                .append(net.kyori.adventure.text.Component.text(messages.text("teams.list-entry", team.getPlayers().size(), economy.getBalance(team.getName()))))));
        return 1;
    }

    private int info(CommandSender sender, String name) {
        TeamModel team = teams.getTeamByName(name);
        if (team == null) { sender.sendMessage(messages.get("teams.not-found", name)); return 0; }
        sender.sendMessage(CommandSupport.teamLabel(team));
        String online = Bukkit.getOnlinePlayers().stream()
                .filter(player -> teams.getPlayerTeam(player) == team)
                .filter(player -> !(sender instanceof Player viewer) || viewer.canSee(player))
                .map(Player::getName).sorted().collect(Collectors.joining(messages.text("general.separator")));
        sender.sendMessage(messages.get("teams.info", team.getPlayers().size(), economy.getBalance(team.getName()),
                online.isEmpty() ? messages.text("general.none") : online));
        return 1;
    }

    private int join(CommandSender sender, String playerName, String teamName) {
        if (game.getState() == GameSession.State.STOPPED) { sender.sendMessage(messages.get("results.frozen")); return 0; }
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) { sender.sendMessage(messages.get("general.player-not-found", playerName)); return 0; }
        TeamModel requested = teams.getTeamByName(teamName);
        if (requested != null && !requested.isPlayable()) { sender.sendMessage(messages.get("teams.not-playable")); return 0; }
        if (!teams.assignPlayerToTeam(target, teamName)) { sender.sendMessage(messages.get("teams.not-found", teamName)); return 0; }
        String canonicalName = teams.getPlayerTeam(target).getName();
        sender.sendMessage(messages.get("teams.join-success-admin", target.getName(), canonicalName));
        target.sendMessage(messages.get("teams.join-success-player", canonicalName));
        return 1;
    }

    private int leave(CommandSender sender, String playerName) {
        if (game.getState() == GameSession.State.STOPPED) { sender.sendMessage(messages.get("results.frozen")); return 0; }
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) { sender.sendMessage(messages.get("general.player-not-found", playerName)); return 0; }
        if (!teams.removePlayerFromTeam(target)) { sender.sendMessage(messages.get("teams.not-in-any-team", target.getName())); return 0; }
        sender.sendMessage(messages.get("teams.leave-success-admin", target.getName()));
        target.sendMessage(messages.get("teams.leave-success-player"));
        return 1;
    }
}
