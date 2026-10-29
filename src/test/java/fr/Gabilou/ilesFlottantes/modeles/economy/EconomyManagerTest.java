package fr.Gabilou.ilesFlottantes.modeles.economy;

import fr.Gabilou.ilesFlottantes.persistence.FileBalanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Vérifie les invariants des caisses d’équipes : précision des montants, refus
 * sans débit, conservation après rechargement et absence de modification
 * en mémoire lorsque la persistance échoue.
 */
class EconomyManagerTest {
    @TempDir Path directory;

    private EconomyManager manager() throws IOException {
        return new EconomyManager(new FileBalanceRepository(directory.resolve("balances.properties")));
    }

    @Test void transfersExactDecimalsAndSurvivesRestart() throws IOException {
        EconomyManager economy = manager();
        economy.setBalance("Bleu clair", new BigDecimal("0.30"));
        assertEquals(EconomyManager.TransferResult.SUCCESS, economy.transfer("Bleu clair", "Rouge", new BigDecimal("0.10")));
        EconomyManager reloaded = manager();
        assertEquals(new BigDecimal("0.20"), reloaded.getBalance("Bleu clair"));
        assertEquals(new BigDecimal("0.10"), reloaded.getBalance("Rouge"));
    }

    @Test void insufficientFundsAndSameTeamDoNotChangeBalances() throws IOException {
        EconomyManager economy = manager();
        economy.setBalance("Bleu", BigDecimal.ONE);
        assertEquals(EconomyManager.TransferResult.INSUFFICIENT_FUNDS, economy.transfer("Bleu", "Rouge", BigDecimal.TEN));
        assertEquals(EconomyManager.TransferResult.SAME_TEAM, economy.transfer("Bleu", "Bleu", BigDecimal.ONE));
        assertEquals(new BigDecimal("1.00"), manager().getBalance("Bleu"));
        assertEquals(new BigDecimal("0.00"), economy.getBalance("Rouge"));
    }

    @Test void invalidAmountsAreRejected() {
        for (String value : new String[]{"NaN", "Infinity", "-1", "1e2", "0.001", "1000000000000", "", "1,2.3"}) {
            assertThrows(IllegalArgumentException.class, () -> EconomyManager.parseAmount(value));
        }
        assertEquals(new BigDecimal("12.50"), EconomyManager.parseAmount("12,50"));
    }

    @Test void zeroAllowedForSetButNotPay() throws IOException {
        EconomyManager economy = manager();
        economy.setBalance("Bleu", BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> economy.transfer("Bleu", "Rouge", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> economy.setBalance("Bleu", new BigDecimal("-1")));
    }

    @Test void recipientLimitDoesNotDebitSender() throws IOException {
        EconomyManager economy = manager();
        economy.setBalance("Bleu", BigDecimal.TEN);
        economy.setBalance("Rouge", EconomyManager.parseAmount("999999999999.99"));
        assertThrows(IllegalArgumentException.class, () -> economy.transfer("Bleu", "Rouge", BigDecimal.ONE));
        assertEquals(new BigDecimal("10.00"), manager().getBalance("Bleu"));
    }

    @Test void failedSaveDoesNotPublishEitherSide() throws IOException {
        EconomyManager economy = new EconomyManager(new BalanceRepository() {
            public Map<String, BigDecimal> load() { return Map.of("Bleu", BigDecimal.TEN); }
            public void save(Map<String, BigDecimal> balances) throws IOException { throw new IOException(); }
        });
        assertThrows(IOException.class, () -> economy.transfer("Bleu", "Rouge", BigDecimal.ONE));
        assertEquals(new BigDecimal("10.00"), economy.getBalance("Bleu"));
        assertEquals(new BigDecimal("0.00"), economy.getBalance("Rouge"));
        assertThrows(IOException.class, () -> economy.setBalance("Bleu", BigDecimal.ZERO));
        assertEquals(new BigDecimal("10.00"), economy.getBalance("Bleu"));
    }

    @Test void corruptFileIsNotSilentlyReset() throws IOException {
        Path file = directory.resolve("balances.properties");
        for (String invalid : new String[]{"Bleu=NaN", "Bleu=-1", "Bleu=0.001"}) {
            Files.writeString(file, invalid);
            assertThrows(IOException.class, this::manager);
            assertEquals(invalid, Files.readString(file));
        }
    }

    @Test void resetClearsEveryTeamAndPersists() throws IOException {
        EconomyManager economy = manager();
        economy.setBalance("Rouge", BigDecimal.TEN);
        economy.setBalance("Bleu", BigDecimal.ONE);
        economy.reset();
        assertEquals(new BigDecimal("0.00"), economy.getBalance("Rouge"));
        assertEquals(new BigDecimal("0.00"), manager().getBalance("Bleu"));
        assertEquals(new BigDecimal("0.00"), manager().getBalance("Rouge"));
    }

    @Test void failedResetKeepsBalances() throws IOException {
        EconomyManager economy = new EconomyManager(new BalanceRepository() {
            public Map<String, BigDecimal> load() { return Map.of("Rouge", BigDecimal.TEN); }
            public void save(Map<String, BigDecimal> balances) throws IOException { throw new IOException(); }
        });
        assertThrows(IOException.class, economy::reset);
        assertEquals(new BigDecimal("10.00"), economy.getBalance("Rouge"));
    }
}
