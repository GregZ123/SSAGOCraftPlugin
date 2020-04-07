package io.trickey.ssagocraft;

import io.trickey.ssagocraft.treefeller.TreeFellerListener;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashSet;

/**
 * The main class of the SSAGO Craft plugin, this holds the plugin
 * initialization code and servers as the main storage for objects related to
 * the plugin.
 *
 * @author trickeydan
 * @author GregZ_
 * @version 2
 * @since 1.0
 */
public final class SSAGOCraftPlugin extends JavaPlugin {

    /**
     * Called when the plugin is enabled by the server, instantiate and store
     * all needed objects.
     */
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new TreeFellerListener(new HashSet<>(Collections.singletonList(Material.GOLDEN_AXE)), 250, true, 3), this);
    }
}
