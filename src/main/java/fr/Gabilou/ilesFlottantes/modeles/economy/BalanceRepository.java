package fr.Gabilou.ilesFlottantes.modeles.economy;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Contrat de persistance des caisses d’équipes, indépendant de Bukkit.
 * <p>Chaque clé est un nom canonique d’équipe, jamais un pseudo ou un UUID de joueur.
 * Une sauvegarde remplace l’ensemble des soldes en une opération indivisible :
 * en cas d’échec, l’état précédemment sauvegardé doit rester utilisable.</p>
 */
public interface BalanceRepository {
    Map<String, BigDecimal> load() throws IOException;
    void save(Map<String, BigDecimal> balances) throws IOException;
}
