package fr.Gabilou.ilesFlottantes.utils;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Charge et formate les textes centralisés dans la ressource {@code messages.yml}.
 * <p>Le fichier du dossier du plugin permet la personnalisation ; les valeurs
 * embarquées servent de repli pour les clés absentes d’une ancienne installation.
 * Les méthodes distinguent les messages préfixés des textes sans préfixe.</p>
 */
public class MessageManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        try (var reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource("messages.yml")), StandardCharsets.UTF_8)) {
            this.config.setDefaults(YamlConfiguration.loadConfiguration(reader));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public String get(String path, Object... args) {
        String prefix = config.getString("prefix", "");
        String raw = Objects.requireNonNull(config.getString(path));
        String formatted = String.format(raw, args);
        return ChatColor.translateAlternateColorCodes('&', prefix + formatted);
    }

    /** Convertit un texte configuré et ses codes couleur en composant Adventure. */
    public net.kyori.adventure.text.Component component(String path, Object... args) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text(path, args));
    }

    public String text(String path, Object... args) {
        return ChatColor.translateAlternateColorCodes('&', String.format(Objects.requireNonNull(config.getString(path)), args));
    }
}
