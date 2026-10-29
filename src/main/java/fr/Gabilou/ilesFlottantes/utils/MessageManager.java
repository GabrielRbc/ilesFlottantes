package fr.Gabilou.ilesFlottantes.utils;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

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
    }

    public String get(String path, Object... args) {
        String prefix = config.getString("prefix", "");
        String raw = config.getString(path, "&c[Message manquant : " + path + "]");
        String formatted = String.format(raw, args);
        return ChatColor.translateAlternateColorCodes('&', prefix + formatted);
    }
}