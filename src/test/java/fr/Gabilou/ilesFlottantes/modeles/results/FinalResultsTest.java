package fr.Gabilou.ilesFlottantes.modeles.results;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static fr.Gabilou.ilesFlottantes.modeles.results.FinalResults.*;

/** Vérifie points, égalités, électeurs figés, unicité des votes et remise à zéro sans serveur Minecraft. */
class FinalResultsTest {
    private final AtomicLong time = new AtomicLong();
    private final FinalResults results = new FinalResults(time::get);
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();

    private Map<String, TeamStats> teams() {
        return Map.of("Rouge", new TeamStats(new BigDecimal("100.00"), 3),
                "Bleu", new TeamStats(new BigDecimal("20.00"), 1));
    }
    private void begin() { assertTrue(results.begin(teams(), Map.of(alice, "Rouge", bob, "Bleu"))); }
    private void voting() {
        begin();
        while (results.revealNext() != null) { }
        assertTrue(results.openVoting(Duration.ofMinutes(30)));
    }

    @Test void eachBossBelongsOnlyToItsFirstTeam() {
        assertTrue(results.claimBoss(Category.DRAGON, "Bleu"));
        assertFalse(results.claimBoss(Category.DRAGON, "Rouge"));
        assertTrue(results.claimBoss(Category.WARDEN, "Rouge"));
        assertFalse(results.claimBoss(Category.MONEY, "Rouge"));
        begin();
        assertFalse(results.claimBoss(Category.WITHER, "Rouge"));
        while (results.revealNext() != null) { }
        assertEquals(Map.of("Rouge", 2, "Bleu", 2), results.points());
        assertTrue(results.awards().stream().filter(a -> a.category() == Category.WITHER).findFirst().orElseThrow().teams().isEmpty());
    }

    @Test void pointsAreRevealedExactlyOnceAndBeginIsIdempotent() {
        begin();
        assertEquals(Map.of("Rouge", 0, "Bleu", 0), results.points());
        assertFalse(results.begin(Map.of(), Map.of()));
        assertEquals(Category.MONEY, results.revealNext().category());
        assertEquals(1, results.points().get("Rouge"));
        while (results.revealNext() != null) { }
        assertNull(results.revealNext());
        assertEquals(Map.of("Rouge", 1, "Bleu", 1), results.points());
    }

    @Test void voteIsUniqueByUuidAndInvalidVotesDoNotConsumeIt() {
        voting();
        assertEquals(VoteResult.OWN_TEAM, results.vote(alice, "Rouge"));
        assertEquals(VoteResult.INVALID_TEAM, results.vote(alice, "Commun"));
        assertEquals(VoteResult.ACCEPTED, results.vote(alice, "Bleu"));
        assertEquals(VoteResult.ALREADY_VOTED, results.vote(UUID.fromString(alice.toString()), "Bleu"));
        assertEquals(1, results.votesCast());
        assertTrue(results.candidates(alice).isEmpty());
    }

    @Test void onlyFrozenElectorateCanVoteAndOnlyForOtherParticipants() {
        voting();
        assertEquals(List.of("Bleu"), results.candidates(alice));
        assertEquals(VoteResult.INELIGIBLE, results.vote(UUID.randomUUID(), "Rouge"));
        assertEquals(2, results.eligibleVoters());
    }

    @Test void allVotesAllowEarlyCloseAndBuildTieAwardsBothTeams() {
        voting();
        assertEquals(VoteResult.ACCEPTED, results.vote(alice, "Bleu"));
        assertFalse(results.votingComplete());
        assertEquals(VoteResult.ACCEPTED, results.vote(bob, "Rouge"));
        assertTrue(results.votingComplete());
        assertEquals(List.of("Bleu", "Rouge"), results.finishVoting().teams());
        assertNull(results.finishVoting());
        assertEquals(Map.of("Bleu", 2, "Rouge", 2), results.points());
        assertTrue(results.winners().isEmpty());
        results.finish();
        assertEquals(List.of("Bleu", "Rouge"), results.winners());
    }

    @Test void adminCanClosePartialVoteAndSelectWinner() {
        voting();
        results.vote(alice, "Bleu");
        assertFalse(results.votingComplete());
        assertEquals(List.of("Bleu"), results.finishVoting().teams());
        assertEquals(VoteResult.CLOSED, results.vote(bob, "Rouge"));
        results.finish();
        assertEquals(List.of("Bleu"), results.winners());
    }

    @Test void thirtyMinuteDeadlineRejectsLateVotesEvenBeforeTick() {
        voting();
        time.set(Duration.ofMinutes(30).toNanos());
        assertEquals(0, results.remainingSeconds());
        assertTrue(results.votingComplete());
        assertEquals(VoteResult.CLOSED, results.vote(alice, "Bleu"));
        assertTrue(results.finishVoting().teams().isEmpty());
    }

    @Test void emptyElectionDoesNotInventBuildPoints() {
        voting();
        assertTrue(results.finishVoting().teams().isEmpty());
        assertEquals(Map.of("Rouge", 1, "Bleu", 1), results.points());
    }

    @Test void tiesInMoneyAndDeathsGiveOnePointToEach() {
        results.begin(Map.of("Rouge", new TeamStats(BigDecimal.ZERO, 0), "Bleu", new TeamStats(new BigDecimal("0.00"), 0)), Map.of());
        while (results.revealNext() != null) { }
        assertEquals(Map.of("Rouge", 2, "Bleu", 2), results.points());
    }

    @Test void oneTeamHasNoPossibleElectorateAndSkipsWaiting() {
        results.begin(Map.of("Rouge", new TeamStats(BigDecimal.ONE, 0)), Map.of(alice, "Rouge"));
        while (results.revealNext() != null) { }
        results.openVoting(Duration.ofMinutes(30));
        assertEquals(0, results.eligibleVoters());
        assertTrue(results.votingComplete());
        assertTrue(results.finishVoting().teams().isEmpty());
    }

    @Test void resetClearsBossesVotesAndPointsAndAllowsNewVote() {
        results.claimBoss(Category.DRAGON, "Rouge");
        voting(); results.vote(alice, "Bleu");
        results.reset();
        assertEquals(Phase.IDLE, results.phase());
        assertTrue(results.points().isEmpty());
        assertEquals(0, results.votesCast());
        assertTrue(results.claimBoss(Category.DRAGON, "Bleu"));
        voting();
        assertEquals(VoteResult.ACCEPTED, results.vote(alice, "Bleu"));
    }

    @Test void cannotVoteBeforeCeremonyOrOpenVotingBeforeAwards() {
        assertEquals(VoteResult.CLOSED, results.vote(alice, "Bleu"));
        begin();
        assertFalse(results.openVoting(Duration.ofMinutes(30)));
        assertThrows(IllegalArgumentException.class, () -> results.openVoting(Duration.ZERO));
    }
}
