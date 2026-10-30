package fr.Gabilou.ilesFlottantes.commandes;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.Gabilou.ilesFlottantes.modeles.territories.*;
import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import fr.Gabilou.ilesFlottantes.utils.MessageManager;
import io.papermc.paper.command.brigadier.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.io.IOException;
import java.util.List;
import java.util.logging.*;

/** Configuration des points de départ depuis la position et l’orientation de l’administrateur. */
public final class SpawnCommand {
    private final TerritoryManager territories;
    private final SpawnService spawns;
    private final TeamManager teams;
    private final MessageManager messages;
    private final Logger logger;

    public SpawnCommand(TerritoryManager territories, SpawnService spawns, TeamManager teams, MessageManager messages, Logger logger) {
        this.territories = territories; this.spawns = spawns; this.teams = teams; this.messages = messages; this.logger = logger;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        var root = Commands.literal("spawn").requires(source -> source.getSender().hasPermission(CommandSupport.ADMIN))
                .executes(ctx -> reply(ctx.getSource().getSender(), "territories.spawn-usage"));
        for (String action : List.of("set", "tp", "info")) {
            root.then(Commands.literal(action).then(Commands.argument("equipe", StringArgumentType.greedyString())
                    .suggests((ctx, b) -> CommandSupport.teams(teams, messages, b, false))
                    .executes(ctx -> execute(ctx.getSource().getSender(), action, StringArgumentType.getString(ctx, "equipe")))));
        }
        return root.build();
    }

    private int execute(CommandSender sender, String action, String name) {
        var team = teams.getTeamByName(name);
        if (team == null) return reply(sender, "teams.not-found", name);
        if (!team.isPlayable()) return reply(sender, "teams.not-playable");
        if (action.equals("info")) {
            SpawnPoint point = territories.spawn(team.getName());
            if (point == null) return reply(sender, "territories.spawn-missing", team.getName());
            return reply(sender, "territories.spawn-info", team.getName(), point.world(), point.x(), point.y(), point.z(),
                    messages.text(spawns.valid(team.getName(), point) ? "territories.valid" : "territories.invalid"));
        }
        if (!(sender instanceof Player player)) return reply(sender, "general.only-players");
        if (action.equals("tp")) return reply(sender, spawns.teleport(player, team.getName()) ? "territories.teleported" : "territories.spawn-invalid", team.getName());
        SpawnPoint point = SpawnService.from(player.getLocation());
        if (!spawns.valid(team.getName(), point)) return reply(sender, "territories.spawn-invalid", team.getName());
        try {
            territories.setSpawn(team.getName(), point);
            return reply(sender, "territories.spawn-set", team.getName());
        } catch (IOException e) {
            logger.log(Level.SEVERE, messages.text("system.territories-save-failed"), e);
            return reply(sender, "territories.save-failed");
        }
    }
    private int reply(CommandSender sender, String key, Object... args) { sender.sendMessage(messages.get(key, args)); return 1; }
}
