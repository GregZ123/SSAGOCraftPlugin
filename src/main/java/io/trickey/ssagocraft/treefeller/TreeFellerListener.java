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

public class TreeFellerListener implements Listener {

    private static final Map<Material, Material> LOG_LEAF_MATCHES;

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

    private final ThreadLocalRandom rand;

    private Set<Material> validTools;
    private int logLimit;
    private boolean popLeaves;
    private int leafSearchRadius;

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

    private boolean damageTool(ItemMeta itemMeta, float damageChance, int itemMaxDurability) {
        Damageable damageable = (Damageable) itemMeta;

        if (damageChance == 1 || rand.nextFloat() < damageChance) {
            damageable.setDamage(damageable.getDamage() + 1);
        }

        return damageable.getDamage() == itemMaxDurability;
    }

    private float calculateItemDamageChance(ItemMeta itemMeta) {
        if (itemMeta.isUnbreakable() || !(itemMeta instanceof Damageable)) {
            return 0;
        }

        int unbreakingLevel = itemMeta.getEnchantLevel(Enchantment.DURABILITY);

        return unbreakingLevel == 0 ? 1 : 1.0f / (1 + unbreakingLevel);
    }
}
