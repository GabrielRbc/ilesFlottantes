package fr.Gabilou.ilesFlottantes.commandes;

import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import fr.Gabilou.ilesFlottantes.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeamCommand implements CommandExecutor {

    private final TeamManager teamManager;
    private final MessageManager messageManager;

    public TeamCommand(TeamManager teamManager, MessageManager messageManager) {
        this.teamManager = teamManager;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length < 2) {
            sender.sendMessage(messageManager.get("teams.usage"));
            return true;
        }

        String subCommand = args[0];

        // --- /team join <joueur> <équipe> ---
        if (subCommand.equalsIgnoreCase("join")) {
            if (args.length < 3) {
                sender.sendMessage(messageManager.get("teams.usage"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(messageManager.get("general.player-not-found", args[1]));
                return true;
            }

            String teamName = args[2];
            boolean success = teamManager.assignPlayerToTeam(target, teamName);

            if (success) {
                sender.sendMessage(messageManager.get("teams.join-success-admin", target.getName(), teamName));
                target.sendMessage(messageManager.get("teams.join-success-player", teamName));
            } else {
                sender.sendMessage(messageManager.get("teams.not-found", teamName));
            }
            return true;
        }

        // --- /team leave <joueur> ---
        if (subCommand.equalsIgnoreCase("leave")) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(messageManager.get("general.player-not-found", args[1]));
                return true;
            }

            boolean removed = teamManager.removePlayerFromTeam(target);

            if (removed) {
                sender.sendMessage(messageManager.get("teams.leave-success-admin", target.getName()));
                target.sendMessage(messageManager.get("teams.leave-success-player"));
            } else {
                sender.sendMessage(messageManager.get("teams.not-in-any-team", target.getName()));
            }
            return true;
        }

        sender.sendMessage(messageManager.get("teams.usage"));
        return true;
    }
}