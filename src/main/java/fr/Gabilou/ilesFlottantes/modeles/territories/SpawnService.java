package fr.Gabilou.ilesFlottantes.modeles.territories;

import fr.Gabilou.ilesFlottantes.modeles.teams.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import java.util.*;

/**
 * Valide les spawns des équipes participantes et gère les téléportations de départ.
 * Une destination doit être dans une zone de son équipe, dans un monde chargé,
 * avec un sol solide et deux blocs libres. Les retours après connexion sont mémorisés
 * pour les membres absents au départ, uniquement pendant la session courante.
 */
public final class SpawnService {
    private final TeamManager teams;
    private final TerritoryManager territories;
    private final Set<UUID> pending = new HashSet<>();
    private Runnable changed = () -> { };
    public void onChange(Runnable changed) { this.changed = changed; }
    public Set<UUID> pending() { return Set.copyOf(pending); }
    public void restorePending(Set<UUID> saved) { pending.clear(); pending.addAll(saved); }

    /** Erreur de préparation : la liste contient les équipes dont le spawn est absent ou invalide. */
    public static final class StartFailure extends RuntimeException {
        private final List<String> teams;
        public StartFailure(List<String> teams) { this.teams = List.copyOf(teams); }
        public List<String> teams() { return teams; }
    }

    public SpawnService(TeamManager teams, TerritoryManager territories) {
        this.teams = teams;
        this.territories = territories;
    }

    public static SpawnPoint from(Location location) {
        return new SpawnPoint(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public Location location(SpawnPoint point) {
        if (point == null) return null;
        World world = Bukkit.getWorld(point.world());
        return world == null ? null : new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
    }

    public boolean valid(String team, SpawnPoint point) {
        Location location = location(point);
        if (location == null) return false;
        World world = location.getWorld();
        if (location.getBlockY() <= world.getMinHeight() || location.getBlockY() >= world.getMaxHeight() - 1
                || !world.getWorldBorder().isInside(location)) return false;
        Region region = territories.at(point.world(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (region == null || !region.team().equals(team)) return false;
        var feet = location.getBlock();
        var head = feet.getRelative(0, 1, 0);
        var floor = feet.getRelative(0, -1, 0);
        return feet.isPassable() && head.isPassable() && !feet.isLiquid() && !head.isLiquid()
                && floor.getType().isSolid() && !hazard(feet.getType()) && !hazard(head.getType()) && !hazard(floor.getType());
    }

    private boolean hazard(Material material) {
        return switch (material) {
            case LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CACTUS, CAMPFIRE, SOUL_CAMPFIRE,
                    POWDER_SNOW, SWEET_BERRY_BUSH, WITHER_ROSE, POINTED_DRIPSTONE -> true;
            default -> false;
        };
    }

    public List<String> invalidTeams() {
        return teams.getTeams().stream().filter(TeamModel::isPlayable).filter(team -> !team.getPlayers().isEmpty())
                .filter(team -> !valid(team.getName(), territories.spawn(team.getName())))
                .map(TeamModel::getName).toList();
    }

    /** Valide tout avant de déplacer ; tente de restaurer les positions si un autre plugin refuse un transfert. */
    public void teleportAtStart() {
        List<String> invalid = invalidTeams();
        if (!invalid.isEmpty()) throw new StartFailure(invalid);
        Map<Player, Location> original = new LinkedHashMap<>();
        Set<UUID> absent = new HashSet<>();
        for (TeamModel team : teams.getTeams()) {
            if (!team.isPlayable()) continue;
            for (GamePlayer member : team.getPlayers()) {
                Player player = Bukkit.getPlayer(member.getId());
                if (player == null) { absent.add(member.getId()); continue; }
                Location destination = location(territories.spawn(team.getName()));
                original.put(player, player.getLocation());
                if (!player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                    original.forEach((moved, old) -> moved.teleport(old, PlayerTeleportEvent.TeleportCause.PLUGIN));
                    throw new StartFailure(List.of(team.getName()));
                }
                player.setFallDistance(0);
            }
        }
        pending.clear();
        pending.addAll(absent);
        changed.run();
    }

    public boolean teleport(Player player, String team) {
        SpawnPoint point = territories.spawn(team);
        if (!valid(team, point)) return false;
        boolean success = player.teleport(location(point), PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (success) player.setFallDistance(0);
        return success;
    }

    /** @return vrai si le joueur n’avait rien en attente ou a rejoint son spawn avec succès */
    public boolean join(Player player) {
        if (!pending.contains(player.getUniqueId())) return true;
        TeamModel team = teams.getPlayerTeam(player);
        if (team != null && teleport(player, team.getName())) { pending.remove(player.getUniqueId()); changed.run(); return true; }
        return false;
    }

    public void reset() { pending.clear(); changed.run(); }
}
