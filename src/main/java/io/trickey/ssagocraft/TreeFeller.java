package io.trickey.ssagocraft;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class TreeFeller implements Listener {

    private static final HashSet<Material> LOG_MATERIALS = (HashSet<Material>) Collections.unmodifiableSet(new HashSet<>(Arrays.asList(Material.ACACIA_LOG, Material.BIRCH_LOG, Material.DARK_OAK_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG, Material.OAK_LOG, Material.SPRUCE_LOG)));
    private static final HashSet<Material> LEAF_MATERIALS = (HashSet<Material>) Collections.unmodifiableSet(new HashSet<>(Arrays.asList(Material.ACACIA_LEAVES, Material.BIRCH_LEAVES, Material.DARK_OAK_LEAVES, Material.BIRCH_LEAVES, Material.JUNGLE_LEAVES, Material.OAK_LEAVES, Material.SPRUCE_LEAVES)));

    private final JavaPlugin plugin;

    public TreeFeller(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public final void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack handStack = player.getInventory().getItemInMainHand();
        Material tool = handStack.getType();
        if (tool == Material.GOLDEN_AXE) {
            Block block = event.getBlock();
            if (LOG_MATERIALS.contains(block.getType())) {
                player.sendMessage("*Chop* *Chop* The power of the golden axe fells the tree.");
                HashSet<Block> logs = new HashSet<>();
                HashSet<Block> leaves = new HashSet<>();
                getTree(block, logs, leaves);
                Material type = logs.iterator().next().getType();
                for (Block log : logs) {
                    log.setType(Material.AIR);
                }
                World world = player.getWorld();
                world.dropItem(block.getLocation(), new ItemStack(type, logs.size()));
            }
        }
    }

    private void checkDirection(Block anchor, BlockFace direction, HashSet<Block> logs, HashSet<Block> leaves) {
        // North:
        Block nextAnchor = anchor.getRelative(direction);
        if (LOG_MATERIALS.contains(nextAnchor.getType()) && !logs.contains(nextAnchor)) {
            logs.add(nextAnchor);
            getTree(nextAnchor, logs, leaves);
        } else if (LEAF_MATERIALS.contains(nextAnchor.getType()) && !leaves.contains(nextAnchor)) {
            leaves.add(nextAnchor);
        }
    }

    private void checkAllDirections(Block anchor, HashSet<Block> logs, HashSet<Block> leaves) {
        checkDirection(anchor, BlockFace.NORTH, logs, leaves);
        checkDirection(anchor, BlockFace.NORTH_EAST, logs, leaves);
        checkDirection(anchor, BlockFace.EAST, logs, leaves);
        checkDirection(anchor, BlockFace.SOUTH_EAST, logs, leaves);
        checkDirection(anchor, BlockFace.SOUTH, logs, leaves);
        checkDirection(anchor, BlockFace.SOUTH_WEST, logs, leaves);
        checkDirection(anchor, BlockFace.WEST, logs, leaves);
        checkDirection(anchor, BlockFace.NORTH_WEST, logs, leaves);
    }


    private void getTree(Block anchor, HashSet<Block> logs, HashSet<Block> leaves) {
        // Limits:
        if (logs.size() > 150) return;

        checkAllDirections(anchor, logs, leaves);

        // Shift anchor one up:
        anchor = anchor.getRelative(BlockFace.UP);

        checkAllDirections(anchor, logs, leaves);
        checkDirection(anchor, BlockFace.SELF, logs, leaves);
    }
}
