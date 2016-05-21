package fi.maailmanloppu.hideblocks;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.blockpatcher.PatcherAPI;
import com.comphenix.blockpatcher.PatcherMod;

public class HideBlockMod extends JavaPlugin implements Listener {
    
    // API reference
    private PatcherAPI api;
    
    @Override
    public void onEnable() {
        api = PatcherMod.getAPI();
        loadConfig();
        
        getServer().getPluginManager().registerEvents(this, this);
    }
    
    @SuppressWarnings("deprecation")
    private void loadConfig() {
        FileConfiguration config = this.getConfig();
        ConfigurationSection overrideCfg = config.getConfigurationSection("overrides");
        if (overrideCfg == null) {
            this.getLogger().warning("No block overrides provided!");
            return;
        }
        int count = 0;
        try {
            for (String key : overrideCfg.getKeys(false)) {
                String value = overrideCfg.getString(key);
                Material from = Material.getMaterial(key);
                Material to = Material.getMaterial(value);
                api.setBlockLookup(from.getId(), to.getId());
                count++;
            }
        } finally {
            this.getLogger().info("Loaded " + count + " overrides from config.yml.");
        }
    }

}
