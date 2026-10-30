package fr.Gabilou.ilesFlottantes.persistence;

import fr.Gabilou.ilesFlottantes.modeles.territories.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Stocke zones et spawns dans un fichier UTF-8 remplacé atomiquement, sans sauvegarder les raids. */
public final class FileTerritoryRepository implements TerritoryRepository {
    private final Path file;
    public FileTerritoryRepository(Path file) { this.file = file; }

    @Override
    public Snapshot load() throws IOException {
        if (!Files.exists(file)) return new Snapshot(Map.of(), Map.of());
        Properties values = new Properties();
        Map<String, Region> regions = new HashMap<>();
        Map<String, SpawnPoint> spawns = new HashMap<>();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            values.load(reader);
            if (!"1".equals(values.getProperty("version"))) throw new IllegalArgumentException();
            for (String key : values.stringPropertyNames()) {
                if (key.equals("version")) continue;
                String[] parts = values.getProperty(key).split("\\|", -1);
                if (key.startsWith("region.") && parts.length == 8) {
                    String id = key.substring(7);
                    regions.put(id, new Region(id, parts[0], new Cuboid(UUID.fromString(parts[1]),
                            Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]),
                            Integer.parseInt(parts[5]), Integer.parseInt(parts[6]), Integer.parseInt(parts[7]))));
                } else if (key.startsWith("spawn.") && parts.length == 6) {
                    spawns.put(key.substring(6), new SpawnPoint(UUID.fromString(parts[0]),
                            Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                            Float.parseFloat(parts[4]), Float.parseFloat(parts[5])));
                } else throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) { throw new IOException(e); }
        return new Snapshot(regions, spawns);
    }

    @Override
    public void save(Snapshot snapshot) throws IOException {
        Properties values = new Properties();
        values.setProperty("version", "1");
        snapshot.regions().forEach((id, region) -> {
            Cuboid b = region.bounds();
            values.setProperty("region." + id, String.join("|", region.team(), b.world().toString(),
                    "" + b.minX(), "" + b.minY(), "" + b.minZ(), "" + b.maxX(), "" + b.maxY(), "" + b.maxZ()));
        });
        snapshot.spawns().forEach((team, p) -> values.setProperty("spawn." + team,
                String.join("|", p.world().toString(), "" + p.x(), "" + p.y(), "" + p.z(), "" + p.yaw(), "" + p.pitch())));
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "territories-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { values.store(writer, null); }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
