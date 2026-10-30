package fr.Gabilou.ilesFlottantes.commandes;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
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
import java.io.IOException;
import java.math.BigDecimal;
import java.util.logging.Level;
import java.util.logging.Logger;
import fr.Gabilou.ilesFlottantes.modeles.game.GameSession;

/**
 * Adapte les commandes économiques Paper aux caisses partagées des équipes.
 * <p>Pour {@code /pay ChatGPT 20}, le pseudo sert uniquement à retrouver l’équipe
 * destinataire : la caisse de l’équipe de l’émetteur est débitée de 20 et celle
 * de l’équipe de ChatGPT est créditée de 20. Aucun joueur ne possède de compte.</p>
 * <p>{@code /pay} et {@code /balance} sont accessibles sans permission particulière ;
 * {@code /set} est réservé aux administrateurs. Les textes proviennent de
 * {@link MessageManager} et les transactions de {@link EconomyManager}.</p>
 */
public final class EconomyCommand {
    private final TeamManager teams;
    private final EconomyManager economy;
    private final MessageManager messages;
    private final Logger logger;
    private final GameSession game;

    public EconomyCommand(TeamManager teams, EconomyManager economy, MessageManager messages, Logger logger, GameSession game) {
        this.teams = teams;
        this.economy = economy;
        this.messages = messages;
        this.logger = logger;
        this.game = game;
    }

    public LiteralCommandNode<CommandSourceStack> payCommand() {
        return Commands.literal("pay")
                .executes(context -> reply(context.getSource().getSender(), "economy.pay-usage"))
                .then(Commands.argument("joueur", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSupport.players(context.getSource().getSender(), builder))
                        .then(amountArgument().executes(context -> pay(context.getSource().getSender(),
                                StringArgumentType.getString(context, "joueur"), StringArgumentType.getString(context, "montant")))))
                .build();
    }

    public LiteralCommandNode<CommandSourceStack> setCommand() {
        return Commands.literal("set")
                .requires(source -> source.getSender().hasPermission(CommandSupport.ADMIN))
                .executes(context -> reply(context.getSource().getSender(), "economy.set-usage"))
                .then(Commands.argument("equipe", StringArgumentType.string())
                        .suggests((context, builder) -> CommandSupport.teams(teams, messages, builder, true))
                        .then(amountArgument().executes(context -> set(context.getSource().getSender(),
                                StringArgumentType.getString(context, "equipe"), StringArgumentType.getString(context, "montant")))))
                .build();
    }

    public LiteralCommandNode<CommandSourceStack> balanceCommand() {
        return Commands.literal("balance").executes(context -> {
            CommandSender sender = context.getSource().getSender();
            if (!(sender instanceof Player player)) return reply(sender, "general.only-players");
            TeamModel team = teams.getPlayerTeam(player);
            if (team == null) return reply(sender, "teams.not-in-any-team", player.getName());
            return reply(sender, "economy.balance", team.getName(), economy.getBalance(team.getName()));
        }).build();
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> amountArgument() {
        // A string preserves decimal precision and lets messages.yml own amount errors.
        return Commands.argument("montant", StringArgumentType.greedyString());
    }

    private int pay(CommandSender sender, String targetName, String rawAmount) {
        if (!(sender instanceof Player player)) return reply(sender, "general.only-players");
        if (game.getState() == GameSession.State.PAUSED || game.getState() == GameSession.State.STOPPED) {
            return reply(sender, "economy.game-unavailable");
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) return reply(sender, "general.player-not-found", targetName);
        TeamModel from = teams.getPlayerTeam(player);
        TeamModel to = teams.getPlayerTeam(target);
        if (from == null) return reply(sender, "teams.not-in-any-team", player.getName());
        if (to == null) return reply(sender, "teams.not-in-any-team", target.getName());
        try {
            BigDecimal amount = EconomyManager.parseAmount(rawAmount);
            EconomyManager.TransferResult result = economy.transfer(from.getName(), to.getName(), amount);
            if (result == EconomyManager.TransferResult.SAME_TEAM) return reply(sender, "economy.same-team");
            if (result == EconomyManager.TransferResult.INSUFFICIENT_FUNDS) {
                return reply(sender, "economy.insufficient-funds", from.getName(), amount, economy.getBalance(from.getName()));
            }
            reply(sender, "economy.pay-success", amount, to.getName());
            Bukkit.getOnlinePlayers().stream().filter(member -> teams.getPlayerTeam(member) == to)
                    .forEach(member -> member.sendMessage(messages.get("economy.pay-received", amount, player.getName())));
            return 1;
        } catch (IllegalArgumentException e) {
            return reply(sender, "economy.invalid-payment");
        } catch (IOException e) {
            return storageError(sender, e);
        }
    }

    private int set(CommandSender sender, String teamName, String rawAmount) {
        if (game.getState() == GameSession.State.STOPPED) return reply(sender, "results.frozen");
        TeamModel team = teams.getTeamByName(teamName);
        if (team == null) return reply(sender, "teams.not-found", teamName);
        try {
            BigDecimal amount = EconomyManager.parseAmount(rawAmount);
            economy.setBalance(team.getName(), amount);
            return reply(sender, "economy.set-success", team.getName(), amount);
        } catch (IllegalArgumentException e) {
            return reply(sender, "economy.invalid-balance");
        } catch (IOException e) {
            return storageError(sender, e);
        }
    }

    private int storageError(CommandSender sender, IOException error) {
        logger.log(Level.SEVERE, messages.text("system.save-failed"), error);
        return reply(sender, "economy.storage-error");
    }

    private int reply(CommandSender sender, String key, Object... args) {
        sender.sendMessage(messages.get(key, args));
        return 1;
    }
}
