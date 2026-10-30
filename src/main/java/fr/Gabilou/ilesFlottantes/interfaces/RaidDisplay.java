package fr.Gabilou.ilesFlottantes.interfaces;

import fr.Gabilou.ilesFlottantes.modeles.territories.TerritoryManager;
import fr.Gabilou.ilesFlottantes.utils.MessageManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import java.util.*;

/** Boss bars globales des zones vulnérables et notifications de fermeture automatique. */
public final class RaidDisplay {
    private final TerritoryManager territories;
    private final MessageManager messages;
    private final Map<String, Bar> bars = new HashMap<>();

    /** Barre et ensemble des joueurs auxquels elle a déjà été envoyée. */
    private record Bar(BossBar bossBar, Set<UUID> viewers) { }

    public RaidDisplay(TerritoryManager territories, MessageManager messages) {
        this.territories = territories; this.messages = messages;
    }

    /** Appel synchrone chaque seconde ; les délais sont réels, même si la partie est en pause. */
    public void tick() {
        for (String expired : territories.expire()) Bukkit.broadcastMessage(messages.get("territories.raid-ended", expired));
        for (String id : new HashSet<>(bars.keySet())) {
            if (territories.get(id) == null || !territories.isOpen(id)) hide(id);
        }
        for (var region : territories.regions()) {
            if (!territories.isOpen(region.id())) continue;
            long seconds = territories.remainingSeconds(region.id());
            Component title = messages.component(seconds < 0 ? "territories.raid-manual" : "territories.raid-timed", region.id(), region.team(), seconds);
            Bar bar = bars.computeIfAbsent(region.id(), id -> new Bar(BossBar.bossBar(title, 1, BossBar.Color.RED, BossBar.Overlay.PROGRESS), new HashSet<>()));
            bar.bossBar().name(title).progress(territories.progress(region.id()));
            Set<UUID> online = new HashSet<>();
            Bukkit.getOnlinePlayers().forEach(player -> {
                online.add(player.getUniqueId());
                if (bar.viewers().add(player.getUniqueId())) player.showBossBar(bar.bossBar());
            });
            bar.viewers().retainAll(online);
        }
    }

    private void hide(String id) {
        Bar bar = bars.remove(id);
        Bukkit.getOnlinePlayers().forEach(player -> player.hideBossBar(bar.bossBar()));
    }

    public void stop() { new HashSet<>(bars.keySet()).forEach(this::hide); }
}
