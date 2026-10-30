package fr.Gabilou.ilesFlottantes.interfaces;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.*;
import fr.Gabilou.ilesFlottantes.persistence.SessionSnapshot.Visitor;
import fr.Gabilou.ilesFlottantes.modeles.territories.SpawnService;

/**
 * Visite des builds en spectateur, sans modification des inventaires ni de la carte.
 * Le mode et le vol précédents sont restitués au reset, à la déconnexion et à la
 * désactivation du plugin. Les connexions pendant la visite passent aussi en spectateur.
 */
public final class SpectatorTour implements Listener {
    private final Map<UUID, Visitor> originals = new HashMap<>();
    private Runnable changed = () -> { };
    public void onChange(Runnable changed) { this.changed = changed; }
    public Map<UUID, Visitor> snapshot() { return Map.copyOf(originals); }
    public boolean active() { return active; }
    public void restore(Map<UUID, Visitor> saved, boolean active) { originals.clear(); originals.putAll(saved); this.active = active; }
    private boolean active;

    public void start() { active = true; Bukkit.getOnlinePlayers().forEach(this::enter); }

    private void enter(Player player) {
        if (!active) { restore(player); return; }
        originals.putIfAbsent(player.getUniqueId(), new Visitor(player.getGameMode().name(), player.getAllowFlight(), player.isFlying(), SpawnService.from(player.getLocation())));
        changed.run();
        player.closeInventory(); player.setGameMode(GameMode.SPECTATOR);
    }

    private void restore(Player player) {
        Visitor original = originals.get(player.getUniqueId());
        if (original == null) return;
        if (player.getGameMode() == GameMode.SPECTATOR) player.setSpectatorTarget(null);
        var point = original.location();
        var world = Bukkit.getWorld(point.world());
        if (world != null) player.teleport(new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch()));
        player.setGameMode(GameMode.valueOf(original.mode())); player.setAllowFlight(original.allowFlight());
        player.setFlying(original.allowFlight() && original.flying()); player.setFallDistance(0);
        player.saveData();
        originals.remove(player.getUniqueId()); changed.run();
    }

    public void stop() { active = false; Bukkit.getOnlinePlayers().forEach(this::restore); changed.run(); }
    @EventHandler public void onJoin(PlayerJoinEvent e) { enter(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { restore(e.getPlayer()); }
}
