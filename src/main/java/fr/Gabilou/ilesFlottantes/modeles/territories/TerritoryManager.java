package fr.Gabilou.ilesFlottantes.modeles.territories;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.function.LongSupplier;

/**
 * Règles de propriété et de vulnérabilité, sans dépendance Bukkit.
 * Les zones ne se chevauchent pas. Les changements persistants sont publiés après
 * sauvegarde ; les raids utilisent une horloge monotone et expirent même sans affichage.
 */
public final class TerritoryManager {
    private final TerritoryRepository repository;
    private final Set<String> teams;
    private final LongSupplier clock;
    private TerritoryRepository.Snapshot snapshot;
    private final Map<String, Opening> openings = new HashMap<>();

    /** Ouverture manuelle (durée nulle) ou limitée, exprimée en nanosecondes monotones. */
    public record Opening(long started, long duration) { }

    public TerritoryManager(TerritoryRepository repository, Set<String> teams) throws IOException {
        this(repository, teams, System::nanoTime);
    }

    public TerritoryManager(TerritoryRepository repository, Set<String> teams, LongSupplier clock) throws IOException {
        this.repository = repository;
        this.teams = Set.copyOf(teams);
        this.clock = clock;
        snapshot = repository.load();
        try {
            for (Region region : snapshot.regions().values()) {
                if (!teams.contains(region.team()) || !region.equals(snapshot.regions().get(region.id()))) throw new IllegalArgumentException();
                for (Region other : snapshot.regions().values()) {
                    if (!region.id().equals(other.id()) && region.bounds().overlaps(other.bounds())) throw new IllegalArgumentException();
                }
            }
            if (!teams.containsAll(snapshot.spawns().keySet())) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { throw new IOException(e); }
    }

    public Collection<Region> regions() { return snapshot.regions().values(); }
    public Region get(String id) { return snapshot.regions().get(id); }
    public SpawnPoint spawn(String team) { return snapshot.spawns().get(team); }

    public Region at(UUID world, int x, int y, int z) {
        return regions().stream().filter(region -> region.bounds().contains(world, x, y, z)).findFirst().orElse(null);
    }

    /** Refuse les collisions et les noms existants ; une modification exige une suppression explicite. */
    public void create(Region region) throws IOException {
        if (!teams.contains(region.team()) || get(region.id()) != null
                || regions().stream().anyMatch(other -> region.bounds().overlaps(other.bounds()))) throw new IllegalArgumentException();
        Map<String, Region> updated = new HashMap<>(snapshot.regions());
        updated.put(region.id(), region);
        commit(new TerritoryRepository.Snapshot(updated, snapshot.spawns()));
    }

    public void remove(String id) throws IOException {
        if (get(id) == null) throw new IllegalArgumentException();
        Map<String, Region> updated = new HashMap<>(snapshot.regions());
        updated.remove(id);
        commit(new TerritoryRepository.Snapshot(updated, snapshot.spawns()));
        openings.remove(id);
    }

    public void setSpawn(String team, SpawnPoint spawn) throws IOException {
        if (!teams.contains(team)) throw new IllegalArgumentException();
        Map<String, SpawnPoint> updated = new HashMap<>(snapshot.spawns());
        updated.put(team, spawn);
        commit(new TerritoryRepository.Snapshot(snapshot.regions(), updated));
    }

    /** Autorise propriétaire ou opérateur ; le hors-zone et les zones ouvertes sont libres. */
    public boolean canBuild(Region region, String playerTeam, boolean operator) {
        return operator || region == null || isOpen(region.id()) || region.team().equals(playerTeam);
    }

    /** Un mécanisme peut agir librement hors protection, ou à l’intérieur d’une même zone verrouillée. */
    public boolean canMoveBlocks(Region source, Region target) {
        return target == null || isOpen(target.id()) || source != null && source.id().equals(target.id());
    }

    public void unlock(String id, int seconds) {
        if (get(id) == null || seconds < 0 || seconds > 86400) throw new IllegalArgumentException();
        openings.put(id, new Opening(clock.getAsLong(), Duration.ofSeconds(seconds).toNanos()));
    }

    public void lock(String id) { openings.remove(id); }
    public void lockAll() { openings.clear(); }
    public boolean isOpen(String id) { return remainingSeconds(id) != 0; }

    /** @return secondes restantes arrondies vers le haut, -1 pour une ouverture manuelle, 0 si verrouillée */
    public long remainingSeconds(String id) {
        Opening opening = openings.get(id);
        if (opening == null) return 0;
        if (opening.duration() == 0) return -1;
        long remaining = opening.duration() - (clock.getAsLong() - opening.started());
        return remaining <= 0 ? 0 : (remaining + 999_999_999L) / 1_000_000_000L;
    }

    public float progress(String id) {
        Opening opening = openings.get(id);
        if (opening == null) return 0;
        if (opening.duration() == 0) return 1;
        return (float) Math.max(0, Math.min(1, 1.0 - (double) (clock.getAsLong() - opening.started()) / opening.duration()));
    }

    /** Retire les raids expirés et renvoie leurs identifiants pour les notifications. */
    public List<String> expire() {
        List<String> expired = openings.keySet().stream().filter(id -> remainingSeconds(id) == 0).toList();
        expired.forEach(openings::remove);
        return expired;
    }

    private void commit(TerritoryRepository.Snapshot updated) throws IOException {
        repository.save(updated);
        snapshot = updated;
    }
}
