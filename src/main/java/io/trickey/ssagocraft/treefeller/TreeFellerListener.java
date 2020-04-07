package io.trickey.ssagocraft.treefeller;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * This is the listener class for the tree feller feature of the SSAGO Craft
 * plugin it contains the logic required to find and remove log stacks when they
 * are broken with an set item(s) searching upwards and outwards from the
 * initially broken log.
 *
 * @author trickeydan
 * @author GregZ_
 * @version 3
 * @since 1.0
 */
public class TreeFellerListener implements Listener {

    /**
     * The mapping of valid log types to their paired leaf type.
     */
    private static final Map<Material, Material> LOG_LEAF_MATCHES;

    /**
     * The set of relative block faces to search a eight block square around a
     * block at the same y level (excluding the center block a three by three
     * square).
     */
    private static final Set<BlockFace> SEARCH_DIRECTIONS_XZ;

    static {
        Map<Material, Material> workingMap = new HashMap<>();
        workingMap.put(Material.ACACIA_LOG, Material.ACACIA_LEAVES);
        workingMap.put(Material.BIRCH_LOG, Material.BIRCH_LEAVES);
        workingMap.put(Material.DARK_OAK_LOG, Material.DARK_OAK_LEAVES);
        workingMap.put(Material.JUNGLE_LOG, Material.JUNGLE_LEAVES);
        workingMap.put(Material.OAK_LOG, Material.OAK_LEAVES);
        workingMap.put(Material.SPRUCE_LOG, Material.SPRUCE_LEAVES);
        LOG_LEAF_MATCHES = Collections.unmodifiableMap(workingMap);

        Set<BlockFace> workingSet = new HashSet<>();
        workingSet.add(BlockFace.NORTH);
        workingSet.add(BlockFace.EAST);
        workingSet.add(BlockFace.SOUTH);
        workingSet.add(BlockFace.WEST);
        workingSet.add(BlockFace.NORTH_EAST);
        workingSet.add(BlockFace.SOUTH_EAST);
        workingSet.add(BlockFace.SOUTH_WEST);
        workingSet.add(BlockFace.NORTH_WEST);
        SEARCH_DIRECTIONS_XZ = Collections.unmodifiableSet(workingSet);
    }

    /**
     * The ThreadLocalRandom instance to use when determining unbreaking
     * enchantment success, this is not thread safe but can be used as the block
     * break event listeners are always called from the servers main thread.
     */
    private final ThreadLocalRandom rand;

    /**
     * The set of items that can be used to to trigger the tree felling, all
     * items in this set must be of a material type that that returns true for
     * {@link Material#isItem()}.
     */
    private Set<Material> validTools;

    /**
     * The maximum number of logs to process before exiting the log search, due
     * to the log finding algorithm this limit may run over by up to seventeen
     * blocks.
     */
    private int logLimit;

    /**
     * If true then leaves within {@link #leafSearchRadius} around each
     * found log will be broken naturally.
     */
    private boolean popLeaves;

    /**
     * The radius around found logs to remove leaves, leaves must be of the
     * appropriate type for the initial log as defined in
     * {@link #LOG_LEAF_MATCHES}. Additionally, {@link #popLeaves} must be true
     * for leaves to be removed.
     */
    private int leafSearchRadius;

    /**
     * Construct a new instance of the TreeFellerListener class, this method
     * must be called from the main server thread and its result will require
     * registering with the server as a listener.
     *
     * @param validTools       The Set of material types that can be used as
     *                         valid tree felling tools, all of these materials
     *                         must be valid as items. If any of these items are
     *                         damageable then they shall be damaged in
     *                         accordance with standard Minecraft calculations
     *                         per block provided that the player is not in
     *                         creative mode.
     * @param logLimit         The maximum number of log blocks to aim to
     *                         process, this is not a limit to the search
     *                         length. Negative values of this shall be
     *                         converted to positive according to
     *                         {@link Math#abs(int)}.
     * @param popLeaves        If true then leaves in the specified radius
     *                         around found logs will be broken naturally.
     * @param leafSearchRadius The radius around found logs to search for
     *                         leaves, leaves must match with the origin log
     *                         type.
     * @throws IllegalStateException    Thrown if this method is called from any
     *                                  thread other then the main server
     *                                  thread.
     * @throws IllegalArgumentException Thrown if the given set of materials
     *                                  contains a material type that cannot be
     *                                  obtained as an item.
     */
    public TreeFellerListener(Set<Material> validTools, int logLimit, boolean popLeaves, int leafSearchRadius) throws IllegalStateException, IllegalArgumentException {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("TreeFellerListener must be initialized from the main Bukkit thread as it uses ThreadLocalRandom.");
        }

        for (Material mat : validTools) {
            if (!mat.isItem()) {
                throw new IllegalArgumentException("Found non item material in valid tools set, material: " + mat);
            }
        }

        this.rand = ThreadLocalRandom.current();

        this.validTools = validTools;
        this.logLimit = Math.abs(logLimit);
        this.popLeaves = popLeaves;
        this.leafSearchRadius = Math.abs(leafSearchRadius);
    }

    /**
     * Process a block break event. If the event has not been cancelled, the
     * player has a valid tool in their hand and the broken block is a valid log
     * this will be processed as a tree felling.
     *
     * @param event The block break event that has triggered this listener
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public final void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player p = event.getPlayer();
        ItemStack heldItem = p.getInventory().getItemInMainHand();
        ItemMeta heldMeta = heldItem.getItemMeta();
        float itemDamageChance = (heldMeta == null || p.getGameMode() == GameMode.CREATIVE) ? 0 : calculateItemDamageChance(heldMeta);
        int itemMaxDurability = heldItem.getType().getMaxDurability();
        Block origin = event.getBlock();
        Material originType = origin.getType();

        List<Block> workingList = new ArrayList<>();

        int foundLogs = 0;

        Material leafType;
        Block centerLog;

        if (!validTools.contains(heldItem.getType()) || !LOG_LEAF_MATCHES.containsKey(originType)) {
            return;
        }

        workingList.add(origin);
        leafType = LOG_LEAF_MATCHES.get(originType);

        while (foundLogs <= logLimit && !workingList.isEmpty()) {
            centerLog = workingList.remove(0);

            if (itemDamageChance > 0 && damageTool(heldMeta, itemDamageChance, itemMaxDurability)) {
                p.getInventory().clear(p.getInventory().getHeldItemSlot());
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1, 1);
                break;
            }

            foundLogs += searchLogXZ(originType, workingList, leafType, centerLog);
            centerLog = centerLog.getRelative(BlockFace.UP);
            if (centerLog.getType() == originType) {
                workingList.add(centerLog);
                centerLog.breakNaturally();
                foundLogs++;
                if (popLeaves) {
                    popLeaves(centerLog, leafType);
                }
            }
            foundLogs += searchLogXZ(originType, workingList, leafType, centerLog);
        }

        heldItem.setItemMeta(heldMeta);
    }

    /**
     * Search the eight block hollow square around a center location with
     * constant y level for blocks batching the origin block material. Any
     * positive result search blocks will have
     * {@link #popLeaves(Block, Material)} called on them with provided
     * that {@link #popLeaves} is true. When a log is found it will be broken to
     * prevent future search iterations finding the same log.
     *
     * @param originType  The block type to match, this is intended to be the
     *                    type of the origin log.
     * @param workingList The list to add found logs blocks to.
     * @param leafType    The type of leaves to match when poping leaves, this
     *                    should be taken from the appropriate entry in
     *                    {@link #LOG_LEAF_MATCHES}
     * @param centerLog   The origin block to search around that the same y
     *                    level.
     * @return The number of logs that were found by this search pass, thins
     * will always be a number between zero and eight
     */
    private int searchLogXZ(Material originType, List<Block> workingList, Material leafType, Block centerLog) {
        Block workingBlock;
        int foundLogs = 0;

        for (BlockFace bf : SEARCH_DIRECTIONS_XZ) {
            workingBlock = centerLog.getRelative(bf);
            if (workingBlock.getType() == originType) {
                workingList.add(workingBlock);
                workingBlock.breakNaturally();
                foundLogs++;
                if (popLeaves) {
                    popLeaves(workingBlock, leafType);
                }
            }
        }

        return foundLogs;
    }

    /**
     * Break the leaf blocks in {@link #leafSearchRadius} around a given center
     * block provided that they match the leafType Material.
     *
     * @param origin   The origin location to break blocks around, this location
     *                 will also be checked for a leaf type match.
     * @param leafType The Material type to accept as leaves and thus break.
     */
    private void popLeaves(Block origin, Material leafType) {
        Block workingBlock;

        for (int x = -leafSearchRadius; x < leafSearchRadius + 1; x++) {
            for (int y = -leafSearchRadius; y < leafSearchRadius + 1; y++) {
                for (int z = -leafSearchRadius; z < leafSearchRadius + 1; z++) {
                    workingBlock = origin.getRelative(x, y, z);
                    if (workingBlock.getType() == leafType) {
                        workingBlock.breakNaturally();
                    }
                }
            }
        }
    }

    /**
     * Damage the item mate by one according to the damage chance, if the items
     * durability is then equal to the items max durability true will be
     * returned indicating that the item should break.
     *
     * @param itemMeta          The item mata to modify, this item meta is
     *                          assumed to be an instance of {@link Damageable},
     *                          if it is not this method will fail.
     * @param damageChance      The chance (0 - 1 [incisive, exclusive]) that
     *                          the given item meta will be damaged by one.
     * @param itemMaxDurability The maximum durability that this tool can reach
     *                          before it will break.
     * @return True if the items end durability is equal to itemMaxDurability
     * indicating that the item should break, otherwise false.
     */
    private boolean damageTool(ItemMeta itemMeta, float damageChance, int itemMaxDurability) {
        Damageable damageable = (Damageable) itemMeta;

        if (damageChance == 1 || rand.nextFloat() < damageChance) {
            damageable.setDamage(damageable.getDamage() + 1);
        }

        return damageable.getDamage() == itemMaxDurability;
    }

    /**
     * Calculate the damage chance of a given item meta based on if it is
     * unbreakable, an instance of Damageable and the level of
     * {@link Enchantment#DURABILITY} enchantment that this item has if any.
     *
     * @param itemMeta The item meta to calculate damage chance against.
     * @return The chance of durability being removed from the item on use as a
     * float value between zero and one.
     */
    private float calculateItemDamageChance(ItemMeta itemMeta) {
        if (itemMeta.isUnbreakable() || !(itemMeta instanceof Damageable)) {
            return 0;
        }

        int unbreakingLevel = itemMeta.getEnchantLevel(Enchantment.DURABILITY);

        return unbreakingLevel == 0 ? 1 : 1.0f / (1 + unbreakingLevel);
    }
}
