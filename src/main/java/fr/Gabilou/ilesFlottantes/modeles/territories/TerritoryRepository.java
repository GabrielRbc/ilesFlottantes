package fr.Gabilou.ilesFlottantes.modeles.territories;

import java.io.IOException;
import java.util.Map;

/** Persistance atomique de la configuration de carte ; les ouvertures de raid restent temporaires. */
public interface TerritoryRepository {
    /** Instantané immuable des zones et des spawns, indexés par identifiant et équipe. */
    record Snapshot(Map<String, Region> regions, Map<String, SpawnPoint> spawns) {
        public Snapshot { regions = Map.copyOf(regions); spawns = Map.copyOf(spawns); }
    }

    Snapshot load() throws IOException;
    void save(Snapshot snapshot) throws IOException;
}
