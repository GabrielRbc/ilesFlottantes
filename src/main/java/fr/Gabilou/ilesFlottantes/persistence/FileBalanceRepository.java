package fr.Gabilou.ilesFlottantes.persistence;

import fr.Gabilou.ilesFlottantes.modeles.economy.BalanceRepository;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Persiste les caisses d’équipes dans un fichier de propriétés encodé en UTF-8.
 * <p>Écrit un fichier temporaire voisin, puis remplace atomiquement le fichier
 * cible pour conserver ensemble le débit et le crédit d’un transfert.
 * Si le système de fichiers refuse cette opération, la sauvegarde échoue.</p>
 * <p>Un fichier absent représente des caisses vides ; un contenu illisible
 * provoque une erreur au lieu de réinitialiser silencieusement les soldes.</p>
 */
public final class FileBalanceRepository implements BalanceRepository {
    private final Path file;

    public FileBalanceRepository(Path file) { this.file = file; }

    @Override
    public Map<String, BigDecimal> load() throws IOException {
        Map<String, BigDecimal> result = new HashMap<>();
        if (!Files.exists(file)) return result;
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            for (String key : properties.stringPropertyNames()) {
                result.put(key, new BigDecimal(properties.getProperty(key)));
            }
        } catch (IllegalArgumentException e) {
            throw new IOException(e);
        }
        return result;
    }

    @Override
    public void save(Map<String, BigDecimal> balances) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "balances-", ".tmp");
        try {
            Properties properties = new Properties();
            balances.forEach((team, amount) -> properties.setProperty(team, amount.toPlainString()));
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, null);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
