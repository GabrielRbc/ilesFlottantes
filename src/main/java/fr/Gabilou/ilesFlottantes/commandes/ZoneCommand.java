package fr.Gabilou.ilesFlottantes.commandes;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.Gabilou.ilesFlottantes.modeles.territories.*;
import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import fr.Gabilou.ilesFlottantes.utils.MessageManager;
import io.papermc.paper.command.brigadier.*;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import java.io.IOException;
import java.util.*;
import java.util.logging.*;

/** Configuration administrative des cuboïdes, sélection de coins et ouvertures de raids. */
public final class ZoneCommand implements Listener {
    private final TerritoryManager territories;
    private final TeamManager teams;
    private final MessageManager messages;
    private final Logger logger;
    private final Map<UUID, Location[]> selections = new HashMap<>();

    public ZoneCommand(TerritoryManager territories, TeamManager teams, MessageManager messages, Logger logger) {
        this.territories = territories; this.teams = teams; this.messages = messages; this.logger = logger;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        var root = Commands.literal("zone").requires(source -> source.getSender().hasPermission(CommandSupport.ADMIN))
                .executes(context -> reply(context.getSource().getSender(), "territories.usage"));
        root.then(position("pos1", 0)).then(position("pos2", 1));
        root.then(Commands.literal("create").then(Commands.argument("zone", StringArgumentType.word())
                .then(Commands.argument("equipe", StringArgumentType.greedyString())
                        .suggests((ctx, b) -> CommandSupport.teams(teams, messages, b, false))
                        .executes(ctx -> create(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "zone"), StringArgumentType.getString(ctx, "equipe"))))));
        root.then(Commands.literal("list").executes(ctx -> {
            if (territories.regions().isEmpty()) return reply(ctx.getSource().getSender(), "territories.empty");
            territories.regions().stream().sorted(Comparator.comparing(Region::id))
                    .forEach(region -> info(ctx.getSource().getSender(), region.id()));
            return 1;
        }));
        for (String action : List.of("info", "delete", "lock", "unlock")) {
            var argument = regionArgument().executes(ctx -> action(ctx.getSource().getSender(), action, StringArgumentType.getString(ctx, "zone"), 0));
            if (action.equals("unlock")) argument.then(Commands.argument("secondes", IntegerArgumentType.integer(1, 86400))
                    .executes(ctx -> action(ctx.getSource().getSender(), action, StringArgumentType.getString(ctx, "zone"), IntegerArgumentType.getInteger(ctx, "secondes"))));
            root.then(Commands.literal(action).then(argument));
        }
        return root.build();
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> regionArgument() {
        return Commands.argument("zone", StringArgumentType.word()).suggests((ctx, b) -> {
            territories.regions().stream().map(Region::id).filter(id -> id.startsWith(b.getRemainingLowerCase())).sorted().forEach(b::suggest);
            return b.buildFuture();
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> position(String name, int index) {
        return Commands.literal(name).executes(ctx -> select(ctx.getSource().getSender(), index, null))
                .then(Commands.argument("x", IntegerArgumentType.integer(-30000000, 30000000))
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer(-30000000, 30000000))
                                        .executes(ctx -> select(ctx.getSource().getSender(), index, new int[]{
                                                IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z")})))));
    }

    private int select(CommandSender sender, int index, int[] coordinates) {
        if (!(sender instanceof Player player)) return reply(sender, "general.only-players");
        Location point = player.getLocation();
        if (coordinates != null) point = new Location(player.getWorld(), coordinates[0], coordinates[1], coordinates[2]);
        if (point.getBlockY() < point.getWorld().getMinHeight() || point.getBlockY() >= point.getWorld().getMaxHeight()) return reply(sender, "territories.invalid-height");
        selections.computeIfAbsent(player.getUniqueId(), id -> new Location[2])[index] = point;
        return reply(sender, "territories.selected", index + 1, point.getWorld().getName(), point.getBlockX(), point.getBlockY(), point.getBlockZ());
    }

    private int create(CommandSender sender, String id, String teamName) {
        if (!(sender instanceof Player player)) return reply(sender, "general.only-players");
        var team = teams.getTeamByName(teamName);
        if (team == null) return reply(sender, "teams.not-found", teamName);
        Location[] points = selections.get(player.getUniqueId());
        if (points == null || points[0] == null || points[1] == null || !points[0].getWorld().equals(points[1].getWorld())) return reply(sender, "territories.selection-required");
        try {
            Location a = points[0], b = points[1];
            territories.create(new Region(id, team.getName(), new Cuboid(a.getWorld().getUID(), a.getBlockX(), a.getBlockY(), a.getBlockZ(), b.getBlockX(), b.getBlockY(), b.getBlockZ())));
            return reply(sender, "territories.created", id, team.getName());
        } catch (IllegalArgumentException e) { return reply(sender, "territories.invalid-region"); }
        catch (IOException e) { return storageError(sender, e); }
    }

    private int action(CommandSender sender, String action, String id, int seconds) {
        if (territories.get(id) == null) return reply(sender, "territories.not-found", id);
        try {
            switch (action) {
                case "info": return info(sender, id);
                case "delete": territories.remove(id); break;
                case "lock": territories.lock(id); break;
                case "unlock": territories.unlock(id, seconds); break;
                default: return 0;
            }
            return reply(sender, "territories." + action + "-success", id,
                    seconds == 0 ? messages.text("territories.manual") : messages.text("territories.seconds", seconds));
        } catch (IOException e) { return storageError(sender, e); }
    }

    private int info(CommandSender sender, String id) {
        Region region = territories.get(id);
        Cuboid b = region.bounds();
        var world = org.bukkit.Bukkit.getWorld(b.world());
        String status = messages.text(territories.isOpen(id) ? "territories.open" : "territories.locked");
        return reply(sender, "territories.info", id, region.team(), world == null ? b.world() : world.getName(),
                b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ(), status);
    }

    private int storageError(CommandSender sender, IOException e) {
        logger.log(Level.SEVERE, messages.text("system.territories-save-failed"), e);
        return reply(sender, "territories.save-failed");
    }
    private int reply(CommandSender sender, String key, Object... args) { sender.sendMessage(messages.get(key, args)); return 1; }
    @EventHandler public void onQuit(PlayerQuitEvent event) { selections.remove(event.getPlayer().getUniqueId()); }
}
