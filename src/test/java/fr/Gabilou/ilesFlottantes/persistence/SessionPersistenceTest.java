package fr.Gabilou.ilesFlottantes.persistence;

import fr.Gabilou.ilesFlottantes.modeles.game.GameSession;
import fr.Gabilou.ilesFlottantes.modeles.results.FinalResults;
import fr.Gabilou.ilesFlottantes.modeles.territories.SpawnPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** Tests de reprise après changement d’horloge, d’intégrité des fichiers et d’unicité des votes/points. */
class SessionPersistenceTest {
    @TempDir Path directory;
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), world = UUID.randomUUID();
    private final AtomicLong clock = new AtomicLong();
    private final GameSession game = new GameSession(clock::get);
    private final FinalResults results = new FinalResults(clock::get);

    private FileSessionRepository repository() { return new FileSessionRepository(directory.resolve("session.json")); }
    private SessionSnapshot snapshot() {
        return new SessionSnapshot(game.snapshot(), results.snapshot(),
                List.of(new SessionSnapshot.SavedPlayer(alice, "Alice", "Rouge", 2, 3),
                        new SessionSnapshot.SavedPlayer(bob, "Bob", "Bleu", 4, 1)), Set.of(bob),
                Map.of(alice, new SessionSnapshot.Visitor("SURVIVAL", false, false, new SpawnPoint(world, 5.5, 65, 7.5, 90, 0))), false);
    }
    private void beginResults() {
        game.start(); game.stop();
        results.claimBoss(FinalResults.Category.DRAGON, "Rouge");
        results.begin(Map.of("Rouge", new FinalResults.TeamStats(BigDecimal.TEN, 3),
                "Bleu", new FinalResults.TeamStats(BigDecimal.ONE, 1)), Map.of(alice, "Rouge", bob, "Bleu"));
    }
    private void openVoting() {
        beginResults(); while (results.revealNext() != null) { }
        results.openVoting(Duration.ofMinutes(30));
    }

    @Test void completeSnapshotRoundTripsIncludingOfflinePlayersAndSpectatorReturn() throws IOException {
        SessionSnapshot before = snapshot(); repository().save(before);
        assertEquals(before, repository().load().orElseThrow());
    }

    @Test void activeCountdownReturnsPausedWithoutCountingDaysOffline() throws IOException {
        game.setTimeLimit(120); game.start(); clock.set(Duration.ofMinutes(17).toNanos());
        repository().save(snapshot()); clock.addAndGet(Duration.ofDays(4).toNanos());
        GameSession restored = new GameSession(clock::get); restored.restore(repository().load().orElseThrow().game());
        assertEquals(GameSession.State.PAUSED, restored.getState());
        assertEquals(Duration.ofMinutes(103), restored.displayedTime());
        clock.addAndGet(Duration.ofHours(1).toNanos());
        assertEquals(Duration.ofMinutes(103), restored.displayedTime());
        restored.start(); clock.addAndGet(Duration.ofMinutes(3).toNanos());
        assertEquals(Duration.ofMinutes(100), restored.displayedTime());
    }

    @Test void stoppedAndReadyStatesAreNotTurnedIntoAnActiveGame() {
        for (GameSession.State state : List.of(GameSession.State.READY, GameSession.State.STOPPED, GameSession.State.PAUSED)) {
            GameSession restored = new GameSession(); restored.restore(new GameSession.Snapshot(state, 0, null));
            assertEquals(state, restored.getState());
        }
    }

    @Test void votesSurviveAndVotingWaitsForManualResume() throws IOException {
        openVoting(); results.vote(alice, "Bleu"); clock.set(Duration.ofMinutes(10).toNanos());
        repository().save(snapshot()); clock.addAndGet(Duration.ofDays(2).toNanos());
        FinalResults restored = new FinalResults(clock::get); restored.restore(repository().load().orElseThrow().results());
        assertEquals(1200, restored.remainingSeconds());
        assertEquals(FinalResults.VoteResult.CLOSED, restored.vote(bob, "Rouge"));
        clock.addAndGet(Duration.ofHours(1).toNanos());
        assertEquals(1200, restored.remainingSeconds());
        restored.resumeVoting();
        assertEquals(FinalResults.VoteResult.ALREADY_VOTED, restored.vote(alice, "Bleu"));
        assertEquals(FinalResults.VoteResult.ACCEPTED, restored.vote(bob, "Rouge"));
        assertTrue(restored.votingComplete());
    }

    @Test void partialCeremonyDoesNotAwardPreviousPointsAgain() throws IOException {
        beginResults(); results.revealNext(); results.revealNext();
        repository().save(snapshot());
        FinalResults restored = new FinalResults(); restored.restore(repository().load().orElseThrow().results());
        assertEquals(2, restored.revealed());
        assertEquals(2, restored.points().get("Rouge"));
        assertEquals(FinalResults.Category.WARDEN, restored.revealNext().category());
        assertEquals(2, restored.points().get("Rouge"));
        assertFalse(restored.claimBoss(FinalResults.Category.DRAGON, "Bleu"));
    }

    @Test void completedBuildAndFinalWinnersSurviveReload() throws IOException {
        openVoting(); results.vote(alice, "Bleu"); results.finishVoting(); results.finish();
        repository().save(snapshot());
        FinalResults restored = new FinalResults(); restored.restore(repository().load().orElseThrow().results());
        assertEquals(results.points(), restored.points()); assertEquals(results.winners(), restored.winners());
        assertNull(restored.finishVoting());
    }

    @Test void latestSaveKeepsPreviousVerifiedBackup() throws IOException {
        SessionSnapshot first = snapshot(); repository().save(first);
        game.start(); clock.set(Duration.ofSeconds(42).toNanos()); repository().save(snapshot());
        assertEquals(first, new FileSessionRepository(repository().backup()).load().orElseThrow());
        assertEquals(Duration.ofSeconds(42).toNanos(), repository().load().orElseThrow().game().elapsedNanos());
    }

    @Test void corruptionIsRejectedAndNeverOverwrittenOrSilentlyRolledBack() throws IOException {
        repository().save(snapshot()); repository().save(snapshot());
        Path file = directory.resolve("session.json"); Files.writeString(file, "broken");
        assertThrows(IOException.class, () -> repository().load());
        assertThrows(IOException.class, () -> repository().save(snapshot()));
        assertEquals("broken", Files.readString(file)); assertTrue(Files.exists(repository().backup()));
    }

    @Test void checksumDetectsAnOtherwiseValidJsonEdit() throws IOException {
        repository().save(snapshot());
        Path file = directory.resolve("session.json"); String text = Files.readString(file);
        Files.writeString(file, text.replace("Alice", "Changed"));
        assertThrows(IOException.class, () -> repository().load());
    }

    @Test void missingPrimaryWithExistingBackupDoesNotBecomeEmptySession() throws IOException {
        repository().save(snapshot()); repository().save(snapshot());
        Files.delete(directory.resolve("session.json"));
        assertThrows(IOException.class, () -> repository().load());
    }

    @Test void resetIntentSurvivesAProcessRestartUntilExplicitCompletion() throws IOException {
        repository().save(snapshot()); repository().beginReset();
        assertTrue(repository().resetting()); assertTrue(repository().load().isPresent());
        repository().finishReset(); assertFalse(repository().resetting());
    }

    @Test void invalidSnapshotIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GameSession.Snapshot(GameSession.State.PAUSED, -1, null));
        assertThrows(IllegalArgumentException.class, () -> new SessionSnapshot.SavedPlayer(alice, "Alice", "Rouge", -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SessionSnapshot(game.snapshot(), results.snapshot(), List.of(), Set.of(alice), Map.of(), false));
    }
}
