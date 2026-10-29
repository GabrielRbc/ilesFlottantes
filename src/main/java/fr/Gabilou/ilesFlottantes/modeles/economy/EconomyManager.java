package fr.Gabilou.ilesFlottantes.modeles.economy;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Gère exclusivement les caisses partagées des équipes, sans compte individuel.
 * <p>Les soldes sont indexés par le nom canonique de l’équipe et calculés avec
 * deux décimales exactes. Les appelants résolvent les pseudos en équipes avant
 * d’appeler ce service, qui ne dépend pas de Bukkit.</p>
 * <p>Chaque modification sauvegarde l’ensemble de la transaction avant de publier
 * les nouveaux soldes en mémoire. Les accès aux caisses sont synchronisés.</p>
 */
public final class EconomyManager {
    /** Résultat d’un transfert validé : effectué, même équipe ou fonds insuffisants. */
    public enum TransferResult { SUCCESS, SAME_TEAM, INSUFFICIENT_FUNDS }

    private final BalanceRepository repository;
    private Map<String, BigDecimal> balances;

    public EconomyManager(BalanceRepository repository) throws IOException {
        this.repository = repository;
        this.balances = new HashMap<>(repository.load());
        try {
            balances.replaceAll((team, amount) -> validate(amount));
        } catch (IllegalArgumentException e) {
            throw new IOException(e);
        }
    }

    public static BigDecimal parseAmount(String value) {
        if (!value.matches("[0-9]{1,12}([.,][0-9]{1,2})?")) {
            throw new IllegalArgumentException();
        }
        return validate(new BigDecimal(value.replace(',', '.')));
    }

    private static BigDecimal validate(BigDecimal amount) {
        if (amount == null || amount.signum() < 0 || amount.compareTo(new BigDecimal("999999999999.99")) > 0) {
            throw new IllegalArgumentException();
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public synchronized BigDecimal getBalance(String team) {
        return balances.getOrDefault(team, new BigDecimal("0.00"));
    }

    public synchronized void setBalance(String team, BigDecimal amount) throws IOException {
        Map<String, BigDecimal> updated = new HashMap<>(balances);
        updated.put(team, validate(amount));
        commit(updated);
    }

    /**
     * Transfère un montant entre deux caisses d’équipes distinctes.
     *
     * @param from nom canonique de l’équipe à débiter, pas un pseudo
     * @param to nom canonique de l’équipe à créditer, pas un pseudo
     * @param amount montant strictement positif, avec au plus deux décimales
     * @return résultat du transfert ; un refus ne modifie aucune caisse
     * @throws IllegalArgumentException si le montant ou le solde résultant est invalide
     * @throws IOException si la sauvegarde échoue ; les soldes en mémoire restent inchangés
     */
    public synchronized TransferResult transfer(String from, String to, BigDecimal amount) throws IOException {
        amount = validate(amount);
        if (amount.signum() == 0) throw new IllegalArgumentException();
        if (from.equals(to)) return TransferResult.SAME_TEAM;
        if (getBalance(from).compareTo(amount) < 0) return TransferResult.INSUFFICIENT_FUNDS;
        Map<String, BigDecimal> updated = new HashMap<>(balances);
        updated.put(from, getBalance(from).subtract(amount));
        updated.put(to, validate(getBalance(to).add(amount)));
        commit(updated);
        return TransferResult.SUCCESS;
    }

    private void commit(Map<String, BigDecimal> updated) throws IOException {
        repository.save(Map.copyOf(updated));
        balances = updated;
    }

    /**
     * Vide toutes les caisses en une seule sauvegarde, y compris les équipes absentes.
     * @throws IOException si la sauvegarde échoue ; les anciens soldes sont conservés
     */
    public synchronized void reset() throws IOException {
        commit(new HashMap<>());
    }
}
