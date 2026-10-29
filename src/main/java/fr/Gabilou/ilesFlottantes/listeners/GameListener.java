package fr.Gabilou.ilesFlottantes.listeners;

import fr.Gabilou.ilesFlottantes.interfaces.GameDisplay;
import fr.Gabilou.ilesFlottantes.modeles.game.GameSession;
import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;

/**
 * Suit les connexions et les statistiques de combat, et suspend les actions des
 * participants pendant la pause. Le monde continue de tourner : les protections
 * d’îles et le gel global des entités ne font pas partie de ce listener.
 */
public final class GameListener implements Listener {
    private final GameSession game;
    private final TeamManager teams;
    private final GameDisplay display;

    public GameListener(GameSession game, TeamManager teams, GameDisplay display) {
        this.game = game;
        this.teams = teams;
        this.display = display;
    }

    private boolean frozen(Player player) {
        return game.getState() == GameSession.State.PAUSED && teams.getPlayerTeam(player) != null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { display.refresh(); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { display.remove(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (game.getState() != GameSession.State.RUNNING || teams.getPlayerTeam(event.getEntity()) == null) return;
        Player victim = event.getEntity();
        teams.getOrCreateGamePlayer(victim).addDeath();
        Player killer = victim.getKiller();
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId()) && teams.getPlayerTeam(killer) != null) {
            teams.getOrCreateGamePlayer(killer).addKill();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (frozen(event.getPlayer()) && event.hasChangedPosition()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && frozen(victim)) event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent attack) {
            if (attack.getDamager() instanceof Player player && frozen(player)) event.setCancelled(true);
            if (attack.getDamager() instanceof Projectile projectile
                    && projectile.getShooter() instanceof Player shooter && frozen(shooter)) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { if (frozen(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { if (frozen(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) { if (frozen(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) { if (frozen(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) { if (frozen(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && frozen(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && frozen(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) { if (frozen(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && frozen(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && frozen(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) { if (frozen(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player && frozen(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) { if (frozen(event.getPlayer())) event.setCancelled(true); }
}
