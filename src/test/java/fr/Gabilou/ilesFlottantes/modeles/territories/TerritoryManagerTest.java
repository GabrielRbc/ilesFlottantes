package fr.Gabilou.ilesFlottantes.modeles.territories;

import fr.Gabilou.ilesFlottantes.persistence.FileTerritoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** Vérifie les frontières, droits, ouvertures, sauvegardes et échecs sans serveur Minecraft. */
class TerritoryManagerTest {
    @TempDir Path directory;
    private final UUID world = UUID.randomUUID();
    private final Set<String> teams = Set.of("Rouge", "Bleu", "Commun");
    private final AtomicLong time = new AtomicLong();

    private TerritoryManager manager() throws IOException {
        return new TerritoryManager(new FileTerritoryRepository(directory.resolve("territories.properties")), teams, time::get);
    }

    private Region region(String id, String team, int min, int max) {
        return new Region(id, team, new Cuboid(world, min, 0, 0, max, 100, 10));
    }

    @Test void cornersAreNormalizedAndBoundariesInclusive() {
        Cuboid box = new Cuboid(world, 10, 100, 10, -10, 0, 0);
        assertTrue(box.contains(world, -10, 0, 0));
        assertTrue(box.contains(world, 10, 100, 10));
        assertFalse(box.contains(world, 11, 50, 5));
        assertFalse(box.contains(world, 0, 101, 5));
        assertFalse(box.contains(UUID.randomUUID(), 0, 50, 5));
    }

    @Test void overlapsAndDuplicateIdsAreRejectedWithoutChangingSavedZones() throws IOException {
        TerritoryManager manager = manager();
        manager.create(region("red", "Rouge", 0, 10));
        assertThrows(IllegalArgumentException.class, () -> manager.create(region("blue", "Bleu", 10, 20)));
        assertThrows(IllegalArgumentException.class, () -> manager.create(region("red", "Rouge", 30, 40)));
        manager.create(region("blue", "Bleu", 11, 20));
        assertEquals(2, manager().regions().size());
        assertThrows(IllegalArgumentException.class, () -> manager.create(region("ghost", "Unknown", 40, 50)));
    }

    @Test void ownerAndOperatorCanBuildButOthersCannot() throws IOException {
        TerritoryManager manager = manager();
        Region red = region("red", "Rouge", 0, 10);
        manager.create(red);
        assertTrue(manager.canBuild(red, "Rouge", false));
        assertFalse(manager.canBuild(red, "Bleu", false));
        assertFalse(manager.canBuild(red, null, false));
        assertTrue(manager.canBuild(red, null, true));
        assertTrue(manager.canBuild(null, null, false));
        assertEquals(red, manager.at(world, 0, 0, 0));
    }

    @Test void commonTeamSupportsSeveralProtectedZones() throws IOException {
        TerritoryManager manager = manager();
        manager.create(region("lobby", "Commun", 0, 10));
        manager.create(region("jump", "Commun", 20, 30));
        assertFalse(manager.canBuild(manager.get("lobby"), "Rouge", false));
        assertTrue(manager.canBuild(manager.get("jump"), "Rouge", true));
        assertEquals(2, manager().regions().size());
    }

    @Test void timedRaidExpiresEvenWithoutCallingExpire() throws IOException {
        TerritoryManager manager = manager();
        Region red = region("red", "Rouge", 0, 10);
        manager.create(red);
        manager.unlock("red", 120);
        assertTrue(manager.canBuild(red, "Bleu", false));
        time.set(Duration.ofSeconds(60).toNanos());
        assertEquals(60, manager.remainingSeconds("red"));
        assertEquals(0.5f, manager.progress("red"));
        time.set(Duration.ofSeconds(120).toNanos());
        assertFalse(manager.canBuild(red, "Bleu", false));
        assertEquals(List.of("red"), manager.expire());
        assertTrue(manager.expire().isEmpty());
    }

    @Test void manualOpeningCanBeLockedAndNeverSurvivesReload() throws IOException {
        TerritoryManager manager = manager();
        manager.create(region("red", "Rouge", 0, 10));
        manager.unlock("red", 0);
        time.set(Duration.ofDays(5).toNanos());
        assertEquals(-1, manager.remainingSeconds("red"));
        assertFalse(manager().isOpen("red"));
        manager.lock("red");
        assertFalse(manager.isOpen("red"));
        manager.unlock("red", 120);
        manager.lockAll();
        assertFalse(manager.isOpen("red"));
        assertNotNull(manager.get("red"));
    }

    @Test void mechanismCannotUseAnotherZoneEvenWithSameOwner() throws IOException {
        TerritoryManager manager = manager();
        Region red = region("red", "Rouge", 0, 10), annex = region("annex", "Rouge", 20, 30);
        manager.create(red); manager.create(annex);
        assertTrue(manager.canMoveBlocks(red, red));
        assertFalse(manager.canMoveBlocks(null, red));
        assertFalse(manager.canMoveBlocks(annex, red));
        assertTrue(manager.canMoveBlocks(red, null));
        manager.unlock("annex", 60);
        assertFalse(manager.canMoveBlocks(annex, red));
        assertTrue(manager.canMoveBlocks(red, annex));
    }

    @Test void spawnsAndOrientationsSurviveReloadAndZoneDeletion() throws IOException {
        TerritoryManager manager = manager();
        Region red = region("red", "Rouge", 0, 10);
        SpawnPoint point = new SpawnPoint(world, 5.5, 65, 5.5, 90, -12);
        manager.create(red); manager.setSpawn("Rouge", point);
        assertEquals(point, manager().spawn("Rouge"));
        manager.unlock("red", 60); manager.remove("red");
        assertNull(manager.get("red"));
        assertFalse(manager.isOpen("red"));
        assertEquals(point, manager().spawn("Rouge"));
    }

    @Test void saveFailureDoesNotExposeAnUnpersistedChange() throws IOException {
        TerritoryManager manager = new TerritoryManager(new TerritoryRepository() {
            public Snapshot load() { return new Snapshot(Map.of(), Map.of()); }
            public void save(Snapshot snapshot) throws IOException { throw new IOException(); }
        }, teams);
        assertThrows(IOException.class, () -> manager.create(region("red", "Rouge", 0, 10)));
        assertTrue(manager.regions().isEmpty());
        assertThrows(IOException.class, () -> manager.setSpawn("Rouge", new SpawnPoint(world, 0, 60, 0, 0, 0)));
        assertNull(manager.spawn("Rouge"));
    }

    @Test void corruptOrUnknownTeamsFailLoadingWithoutOverwriting() throws IOException {
        Path file = directory.resolve("territories.properties");
        Files.writeString(file, "version=1\nregion.invalid=corrupted");
        assertThrows(IOException.class, this::manager);
        assertTrue(Files.readString(file).contains("corrupted"));
        new FileTerritoryRepository(file).save(new TerritoryRepository.Snapshot(Map.of("red", region("red", "Unknown", 0, 10)), Map.of()));
        assertThrows(IOException.class, this::manager);
    }

    @Test void invalidDurationsCoordinatesAndNamesAreRejected() throws IOException {
        TerritoryManager manager = manager();
        manager.create(region("red", "Rouge", 0, 10));
        assertThrows(IllegalArgumentException.class, () -> manager.unlock("red", -1));
        assertThrows(IllegalArgumentException.class, () -> manager.unlock("red", 86401));
        assertThrows(IllegalArgumentException.class, () -> manager.unlock("missing", 60));
        assertThrows(IllegalArgumentException.class, () -> region("with space", "Rouge", 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new SpawnPoint(world, Double.NaN, 60, 0, 0, 0));
    }
}
