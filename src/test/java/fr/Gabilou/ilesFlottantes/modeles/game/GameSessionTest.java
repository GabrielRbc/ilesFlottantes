package fr.Gabilou.ilesFlottantes.modeles.game;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** Vérifie les transitions et le temps actif sans attente réelle ni serveur Minecraft. */
class GameSessionTest {
    private final AtomicLong clock = new AtomicLong();
    private final GameSession game = new GameSession(clock::get);

    private void advance(long seconds) { clock.addAndGet(Duration.ofSeconds(seconds).toNanos()); }

    @Test void readyTimeDoesNotCount() {
        advance(100);
        assertEquals(Duration.ZERO, game.elapsed());
        assertFalse(game.pause());
        assertFalse(game.stop());
        assertEquals(GameSession.State.READY, game.getState());
    }

    @Test void pauseAndResumeExcludePausedTime() {
        assertTrue(game.start());
        advance(20);
        assertTrue(game.pause());
        advance(100);
        assertEquals(Duration.ofSeconds(20), game.elapsed());
        assertFalse(game.pause());
        assertTrue(game.start());
        advance(5);
        assertEquals(Duration.ofSeconds(25), game.elapsed());
    }

    @Test void duplicateStartDoesNotResetClock() {
        game.start();
        advance(20);
        assertFalse(game.start());
        advance(5);
        assertEquals(Duration.ofSeconds(25), game.elapsed());
    }

    @Test void stopIsTerminalUntilReset() {
        game.start();
        advance(20);
        assertTrue(game.stop());
        advance(50);
        assertFalse(game.start());
        assertFalse(game.pause());
        assertFalse(game.stop());
        assertEquals(Duration.ofSeconds(20), game.elapsed());
        game.reset();
        assertEquals(GameSession.State.READY, game.getState());
        assertEquals(Duration.ZERO, game.elapsed());
        assertTrue(game.start());
        advance(2);
        assertEquals(Duration.ofSeconds(2), game.elapsed());
    }

    @Test void stoppingPausedGamePreservesItsTime() {
        game.start();
        advance(8);
        game.pause();
        advance(50);
        assertTrue(game.stop());
        assertEquals(Duration.ofSeconds(8), game.elapsed());
    }

    @Test void resetWorksFromEveryState() {
        for (GameSession.State state : GameSession.State.values()) {
            GameSession session = new GameSession(clock::get);
            if (state != GameSession.State.READY) session.start();
            advance(10);
            if (state == GameSession.State.PAUSED) session.pause();
            if (state == GameSession.State.STOPPED) session.stop();
            session.reset();
            assertEquals(Duration.ZERO, session.elapsed());
            assertEquals(GameSession.State.READY, session.getState());
        }
    }

    @Test void twoHourCountdownStopsOnceAtZero() {
        assertTrue(game.setTimeLimit(120));
        assertEquals(Duration.ofHours(2), game.displayedTime());
        advance(60);
        assertEquals(Duration.ofHours(2), game.displayedTime());
        game.start();
        advance(60);
        assertEquals(Duration.ofMinutes(119), game.displayedTime());
        assertFalse(game.finishIfExpired());
        advance(8000);
        assertTrue(game.finishIfExpired());
        assertEquals(Duration.ZERO, game.displayedTime());
        assertEquals(Duration.ofHours(2), game.elapsed());
        assertEquals(GameSession.State.STOPPED, game.getState());
        assertFalse(game.finishIfExpired());
        assertFalse(game.start());
    }

    @Test void countdownPauseDoesNotConsumeRemainingTime() {
        game.setTimeLimit(2);
        game.start();
        advance(30);
        game.pause();
        advance(200);
        assertFalse(game.finishIfExpired());
        assertEquals(Duration.ofSeconds(90), game.displayedTime());
        game.start();
        advance(90);
        assertTrue(game.finishIfExpired());
    }

    @Test void timeConfigurationIsLockedAfterStartAndResetRestoresUnlimitedMode() {
        assertThrows(IllegalArgumentException.class, () -> game.setTimeLimit(-1));
        assertThrows(IllegalArgumentException.class, () -> game.setTimeLimit(Integer.MAX_VALUE));
        game.setTimeLimit(120);
        assertTrue(game.isCountdown());
        game.setTimeLimit(0);
        assertFalse(game.isCountdown());
        game.setTimeLimit(60);
        game.start();
        assertFalse(game.setTimeLimit(10));
        game.pause();
        assertFalse(game.setTimeLimit(10));
        game.stop();
        assertFalse(game.setTimeLimit(10));
        game.reset();
        assertFalse(game.isCountdown());
        assertEquals(Duration.ZERO, game.displayedTime());
        game.start();
        advance(7200);
        assertFalse(game.finishIfExpired());
        assertEquals(Duration.ofHours(2), game.displayedTime());
    }

    @Test void pauseAtDeadlineCannotPreventAutomaticStop() {
        game.setTimeLimit(1);
        game.start();
        advance(60);
        assertFalse(game.pause());
        assertEquals(GameSession.State.STOPPED, game.getState());
    }
}
