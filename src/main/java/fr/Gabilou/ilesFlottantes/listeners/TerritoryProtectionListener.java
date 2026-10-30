package fr.Gabilou.ilesFlottantes.listeners;

import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import fr.Gabilou.ilesFlottantes.modeles.territories.*;
import fr.Gabilou.ilesFlottantes.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.player.*;
import org.bukkit.event.world.StructureGrowEvent;

/**
 * Protège les blocs et éléments décoratifs des zones verrouillées. Les opérateurs
 * contournent les contrôles directs ; les mécanismes sans acteur ne peuvent pas
 * traverser une frontière protégée. Les boutons, portes et déplacements restent libres.
 */
public final class TerritoryProtectionListener implements Listener {
    private final TerritoryManager territories;
    private final TeamManager teams;
    private final MessageManager messages;

    public TerritoryProtectionListener(TerritoryManager territories, TeamManager teams, MessageManager messages) {
        this.territories = territories; this.teams = teams; this.messages = messages;
    }

    private Region region(Location location) {
        return territories.at(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private boolean protectedAt(Location location) {
        Region region = region(location);
        return region != null && !territories.isOpen(region.id());
    }

    private boolean allowed(Player player, Location location) {
        var team = teams.getPlayerTeam(player);
        return territories.canBuild(region(location), team == null ? null : team.getName(), player.isOp());
    }

    private boolean deny(Player player, Location location) {
        if (allowed(player, location)) return false;
        player.sendActionBar(messages.component("territories.denied", region(location).team()));
        return true;
    }

    private boolean mechanism(Block source, Block target) {
        return territories.canMoveBlocks(region(source.getLocation()), region(target.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { if (deny(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (deny(e.getPlayer(), e.getBlockPlaced().getLocation())) e.setCancelled(true);
        if (e instanceof BlockMultiPlaceEvent multiple) {
            if (multiple.getReplacedBlockStates().stream().anyMatch(block -> deny(e.getPlayer(), block.getLocation()))) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        Block block = e.getClickedBlock();
        if (block == null || allowed(e.getPlayer(), block.getLocation())) return;
        if (e.getAction() == Action.PHYSICAL) {
            if (block.getType() == Material.FARMLAND || block.getType() == Material.TURTLE_EGG) e.setCancelled(true);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setUseItemInHand(Event.Result.DENY);
            var data = block.getBlockData();
            if (!(data instanceof Switch || data instanceof Door || data instanceof TrapDoor || data instanceof Gate)) {
                e.setUseInteractedBlock(Event.Result.DENY);
                deny(e.getPlayer(), block.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) { if (deny(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) { if (deny(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent e) { e.blockList().removeIf(block -> protectedAt(block.getLocation())); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent e) { e.blockList().removeIf(block -> protectedAt(block.getLocation())); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) { if (protectedAt(e.getBlock().getLocation())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (e.getPlayer() != null ? deny(e.getPlayer(), e.getBlock().getLocation()) : protectedAt(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) { if (!mechanism(e.getBlock(), e.getToBlock())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) { if (!mechanism(e.getSource(), e.getBlock())) e.setCancelled(true); }

    private boolean piston(Block piston, java.util.List<Block> blocks, BlockFace direction) {
        if (piston.getBlockData() instanceof Directional data && !mechanism(piston, piston.getRelative(data.getFacing()))) return false;
        return blocks.stream().allMatch(block -> mechanism(piston, block) && mechanism(piston, block.getRelative(direction)));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent e) { if (!piston(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent e) { if (!piston(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        if (e.getBlock().getBlockData() instanceof Directional directional
                && !mechanism(e.getBlock(), e.getBlock().getRelative(directional.getFacing()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobChange(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof Player player ? deny(player, e.getBlock().getLocation()) : protectedAt(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() == null ? protectedAt(e.getEntity().getLocation()) : deny(e.getPlayer(), e.getEntity().getLocation())) e.setCancelled(true);
        if (e.getPlayer() == null ? protectedAt(e.getBlock().getLocation()) : deny(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    private Player actor(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        Player player = e instanceof HangingBreakByEntityEvent attack ? actor(attack.getRemover()) : null;
        if (player == null ? protectedAt(e.getEntity().getLocation()) : deny(player, e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDecorDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Hanging || e.getEntity() instanceof ArmorStand)) return;
        Player player = e instanceof EntityDamageByEntityEvent attack ? actor(attack.getDamager()) : null;
        if (player == null ? protectedAt(e.getEntity().getLocation()) : deny(player, e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent e) {
        if (e.getPlayer() == null ? protectedAt(e.getEntity().getLocation()) : deny(e.getPlayer(), e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if ((e.getRightClicked() instanceof Hanging || e.getRightClicked() instanceof ArmorStand)
                && deny(e.getPlayer(), e.getRightClicked().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent e) {
        if ((e.getRightClicked() instanceof Hanging || e.getRightClicked() instanceof ArmorStand)
                && deny(e.getPlayer(), e.getRightClicked().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) { if (deny(e.getPlayer(), e.getRightClicked().getLocation())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTree(StructureGrowEvent e) {
        e.getBlocks().removeIf(block -> e.getPlayer() != null ? !allowed(e.getPlayer(), block.getLocation())
                : !mechanism(e.getLocation().getBlock(), block.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent e) {
        e.getBlocks().removeIf(block -> e.getPlayer() != null ? !allowed(e.getPlayer(), block.getLocation())
                : !mechanism(e.getBlock(), block.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSponge(SpongeAbsorbEvent e) {
        e.getBlocks().removeIf(block -> !mechanism(e.getBlock(), block.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSign(SignChangeEvent e) { if (deny(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityForm(EntityBlockFormEvent e) {
        if (protectedAt(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        for (Location from : inventoryLocations(e.getSource())) {
            for (Location to : inventoryLocations(e.getDestination())) {
                if (!mechanism(from.getBlock(), to.getBlock()) || !mechanism(to.getBlock(), from.getBlock())) e.setCancelled(true);
            }
        }
    }

    private java.util.List<Location> inventoryLocations(Inventory inventory) {
        java.util.List<Location> result = new java.util.ArrayList<>();
        if (inventory.getHolder() instanceof DoubleChest chest) {
            if (chest.getLeftSide() instanceof org.bukkit.block.BlockState left) result.add(left.getLocation());
            if (chest.getRightSide() instanceof org.bukkit.block.BlockState right) result.add(right.getLocation());
        } else if (inventory.getLocation() != null) {
            result.add(inventory.getLocation());
        }
        return result;
    }

    private boolean inventoryAllowed(Player player, Inventory inventory) {
        return inventoryLocations(inventory).stream().allMatch(location -> allowed(player, location));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player player && !inventoryAllowed(player, e.getInventory())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player && !inventoryAllowed(player, e.getView().getTopInventory())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player player && !inventoryAllowed(player, e.getView().getTopInventory())) e.setCancelled(true);
    }
}
