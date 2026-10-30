package fr.Gabilou.ilesFlottantes.modeles.results;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.function.LongSupplier;

/**
 * Points et scrutin d’une partie, indépendants de Bukkit. Les données sont figées
 * au début de la cérémonie ; chaque catégorie rapporte un point à chaque équipe
 * ex æquo. Un boss rapporte seulement le point de sa première élimination.
 */
public final class FinalResults {
    /** Phases du résultat ; le jeu reste arrêté pendant toute la cérémonie. */
    public enum Phase { IDLE, AWARDS, VOTING, REVEAL, FINISHED }
    /** Catégories disponibles, dans l’ordre de présentation. */
    public enum Category { MONEY, DRAGON, WARDEN, DEATHS, WITHER, BUILD }
    /** Validation d’un vote ; seul ACCEPTED consomme le droit de vote. */
    public enum VoteResult { ACCEPTED, CLOSED, INELIGIBLE, ALREADY_VOTED, INVALID_TEAM, OWN_TEAM }
    /** Mesures immuables d’une équipe participante à la fin du jeu. */
    public record TeamStats(BigDecimal money, long deaths) { }
    /** Catégorie attribuée, bénéficiaires et valeur de comparaison affichable. */
    public record Award(Category category, List<String> teams, String value) {
        public Award { teams = List.copyOf(teams); }
    }

    private final LongSupplier clock;
    private final Map<Category, String> bosses = new EnumMap<>(Category.class);
    private final Map<String, Integer> points = new TreeMap<>();
    private final Map<UUID, String> votes = new HashMap<>();
    private Map<UUID, String> electorate = Map.of();
    private List<Award> awards = List.of();
    private Phase phase = Phase.IDLE;
    private int revealed;
    private long voteStarted;
    private long voteDuration;
    private boolean votePaused;
    private Runnable changed = () -> { };

    /** État complet des points et du scrutin ; la durée enregistrée est le temps restant. */
    public record Snapshot(Map<Category, String> bosses, Map<String, Integer> points, Map<UUID, String> votes,
                           Map<UUID, String> electorate, List<Award> awards, Phase phase, int revealed, long remainingNanos) {
        public Snapshot {
            bosses = Map.copyOf(bosses); points = Map.copyOf(points); votes = Map.copyOf(votes);
            electorate = Map.copyOf(electorate); awards = List.copyOf(awards); Objects.requireNonNull(phase);
            Map<UUID, String> voters = electorate;
            if (revealed < 0 || revealed > Math.min(5, awards.size()) || remainingNanos < 0
                    || points.values().stream().anyMatch(value -> value < 0 || value > 6)
                    || !electorate.keySet().containsAll(votes.keySet()) || !points.keySet().containsAll(votes.values())
                    || votes.entrySet().stream().anyMatch(vote -> vote.getValue().equals(voters.get(vote.getKey())))) throw new IllegalArgumentException();
        }
    }
    public void onChange(Runnable changed) { this.changed = changed; }
    private long remainingNanos() { return phase != Phase.VOTING ? 0 : Math.max(0, voteDuration - (votePaused ? 0 : clock.getAsLong() - voteStarted)); }
    public Snapshot snapshot() { return new Snapshot(bosses, points, votes, electorate, awards, phase, revealed, remainingNanos()); }
    public void restore(Snapshot saved) {
        bosses.clear(); bosses.putAll(saved.bosses()); points.clear(); points.putAll(saved.points());
        votes.clear(); votes.putAll(saved.votes()); electorate = saved.electorate(); awards = saved.awards();
        phase = saved.phase(); revealed = saved.revealed(); voteDuration = saved.remainingNanos();
        voteStarted = clock.getAsLong(); votePaused = true;
    }
    public int revealed() { return revealed; }
    public void pauseVoting() { if (!votePaused) { voteDuration = remainingNanos(); votePaused = true; } }
    public void resumeVoting() { if (votePaused) { voteStarted = clock.getAsLong(); votePaused = false; } }


    public FinalResults() { this(System::nanoTime); }
    public FinalResults(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }
    public Phase phase() { return phase; }
    public Map<String, Integer> points() { return Map.copyOf(points); }
    public List<Award> awards() { return awards; }
    public int votesCast() { return votes.size(); }
    public int eligibleVoters() { return electorate.size(); }
    public boolean suspended() { return votePaused && phase != Phase.IDLE && phase != Phase.FINISHED; }

    /** Enregistre un premier boss ; l’appelant vérifie que la partie est active. */
    public boolean claimBoss(Category category, String team) {
        if (phase != Phase.IDLE || team == null || !(category == Category.DRAGON || category == Category.WARDEN || category == Category.WITHER)) return false;
        boolean added = bosses.putIfAbsent(category, team) == null;
        if (added) changed.run();
        return added;
    }

    /** Fige les équipes et électeurs une seule fois, avant toute présentation. */
    public boolean begin(Map<String, TeamStats> teams, Map<UUID, String> players) {
        if (phase != Phase.IDLE) return false;
        teams.keySet().forEach(team -> points.put(team, 0));
        Map<UUID, String> eligible = new HashMap<>();
        if (teams.size() > 1) players.forEach((id, team) -> { if (teams.containsKey(team)) eligible.put(id, team); });
        electorate = Map.copyOf(eligible);
        List<Award> computed = new ArrayList<>();
        BigDecimal money = teams.values().stream().map(TeamStats::money).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        long deaths = teams.values().stream().mapToLong(TeamStats::deaths).min().orElse(0);
        for (Category category : List.of(Category.MONEY, Category.DRAGON, Category.WARDEN, Category.DEATHS, Category.WITHER)) {
            List<String> winners;
            String value;
            if (category == Category.MONEY) {
                winners = teams.keySet().stream().filter(team -> teams.get(team).money().compareTo(money) == 0).sorted().toList();
                value = money.toPlainString();
            } else if (category == Category.DEATHS) {
                winners = teams.keySet().stream().filter(team -> teams.get(team).deaths() == deaths).sorted().toList();
                value = Long.toString(deaths);
            } else {
                String winner = bosses.get(category);
                winners = winner != null && teams.containsKey(winner) ? List.of(winner) : List.of();
                value = "";
            }
            computed.add(new Award(category, winners, value));
        }
        awards = List.copyOf(computed);
        phase = Phase.AWARDS;
        changed.run();
        return true;
    }

    /** Attribue la prochaine catégorie une seule fois, au rythme de l’animation. */
    public Award revealNext() {
        if (phase != Phase.AWARDS || revealed >= awards.size()) return null;
        Award award = awards.get(revealed++);
        award.teams().forEach(team -> points.merge(team, 1, Integer::sum));
        changed.run();
        return award;
    }

    public boolean openVoting(Duration duration) {
        if (duration.isZero() || duration.isNegative()) throw new IllegalArgumentException();
        if (phase != Phase.AWARDS || revealed != awards.size()) return false;
        voteStarted = clock.getAsLong(); voteDuration = duration.toNanos(); votePaused = false; phase = Phase.VOTING;
        changed.run();
        return true;
    }

    public List<String> candidates(UUID voter) {
        if (phase != Phase.VOTING || votePaused || !electorate.containsKey(voter) || votes.containsKey(voter)) return List.of();
        return points.keySet().stream().filter(team -> !team.equals(electorate.get(voter))).toList();
    }

    public VoteResult vote(UUID voter, String team) {
        if (phase != Phase.VOTING || votePaused || remainingSeconds() == 0) return VoteResult.CLOSED;
        if (!electorate.containsKey(voter)) return VoteResult.INELIGIBLE;
        if (votes.containsKey(voter)) return VoteResult.ALREADY_VOTED;
        if (!points.containsKey(team)) return VoteResult.INVALID_TEAM;
        if (team.equals(electorate.get(voter))) return VoteResult.OWN_TEAM;
        votes.put(voter, team);
        changed.run();
        return VoteResult.ACCEPTED;
    }

    public long remainingSeconds() {
        if (phase != Phase.VOTING) return 0;
        long remaining = remainingNanos();
        return remaining <= 0 ? 0 : (remaining + 999_999_999L) / 1_000_000_000L;
    }

    public boolean votingComplete() {
        return phase == Phase.VOTING && !votePaused && (remainingSeconds() == 0 || votes.size() == electorate.size());
    }

    /** Clôture anticipée autorisée par l’appelant admin ; zéro vote n’attribue aucun point. */
    public Award finishVoting() {
        if (phase != Phase.VOTING) return null;
        Map<String, Long> counts = new HashMap<>();
        votes.values().forEach(team -> counts.merge(team, 1L, Long::sum));
        long best = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<String> winners = counts.keySet().stream().filter(team -> counts.get(team) == best).sorted().toList();
        Award award = new Award(Category.BUILD, winners, Long.toString(best));
        winners.forEach(team -> points.merge(team, 1, Integer::sum));
        List<Award> updated = new ArrayList<>(awards); updated.add(award); awards = List.copyOf(updated);
        phase = Phase.REVEAL;
        changed.run();
        return award;
    }

    public List<String> winners() {
        if (phase != Phase.FINISHED) return List.of();
        int best = points.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return points.keySet().stream().filter(team -> points.get(team) == best).toList();
    }

    public void finish() { if (phase == Phase.REVEAL) { phase = Phase.FINISHED; changed.run(); } }

    /** Efface boss, points, électeurs, votes et échéances pour la partie suivante. */
    public void reset() {
        bosses.clear(); points.clear(); votes.clear(); electorate = Map.of(); awards = List.of();
        phase = Phase.IDLE; revealed = 0; voteStarted = 0; voteDuration = 0; votePaused = false; changed.run();
    }
}
