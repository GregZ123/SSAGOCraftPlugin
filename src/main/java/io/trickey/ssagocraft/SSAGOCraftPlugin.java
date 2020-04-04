package io.trickey.ssagocraft;

import io.trickey.ssagocraft.treefeller.TreeFellerListener;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashSet;

public final class SSAGOCraftPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new TreeFellerListener(new HashSet<>(Collections.singletonList(Material.GOLDEN_AXE)), 250, true, 3), this);
    }
}
