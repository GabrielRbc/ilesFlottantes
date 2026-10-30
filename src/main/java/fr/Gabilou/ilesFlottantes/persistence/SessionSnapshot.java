package fr.Gabilou.ilesFlottantes.persistence;

import fr.Gabilou.ilesFlottantes.modeles.game.GameSession;
import fr.Gabilou.ilesFlottantes.modeles.results.FinalResults;
import fr.Gabilou.ilesFlottantes.modeles.territories.SpawnPoint;
import java.util.*;

/** Instantané de session sans objets Bukkit, sérialisable et testable hors serveur. */
public record SessionSnapshot(GameSession.Snapshot game, FinalResults.Snapshot results,
                              List<SavedPlayer> players, Set<UUID> pendingSpawns,
                              Map<UUID, Visitor> visitors, boolean tourActive) {
    public SessionSnapshot {
        Objects.requireNonNull(game); Objects.requireNonNull(results);
        players = List.copyOf(players); pendingSpawns = Set.copyOf(pendingSpawns); visitors = Map.copyOf(visitors);
        Set<UUID> ids = new HashSet<>();
        if (players.stream().anyMatch(player -> !ids.add(player.id())) || !ids.containsAll(pendingSpawns)) throw new IllegalArgumentException();
        if (game.state() != GameSession.State.STOPPED && results.phase() != FinalResults.Phase.IDLE) throw new IllegalArgumentException();
    }

    /** Identité, statistiques et appartenance éventuelle d’un joueur, même hors ligne. */
    public record SavedPlayer(UUID id, String name, String team, int kills, int deaths) {
        public SavedPlayer {
            Objects.requireNonNull(id); Objects.requireNonNull(name);
            if (name.isBlank() || kills < 0 || deaths < 0) throw new IllegalArgumentException();
        }
    }

    /** Mode et position d’avant visite, conservés jusqu’à restauration effective du joueur. */
    public record Visitor(String mode, boolean allowFlight, boolean flying, SpawnPoint location) {
        public Visitor {
            if (!Set.of("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR").contains(mode)) throw new IllegalArgumentException();
            Objects.requireNonNull(location);
        }
    }
}
