 package live.supeer.apied;

import co.aikar.idb.DB;
import org.bukkit.plugin.java.JavaPlugin;

public final class Apied extends JavaPlugin {

    private static Apied plugin;
    public static Apied getInstance() {
        return plugin;
    }
    public static ApiedConfig configuration;


    @Override
    public void onEnable() {
        plugin = this;
        configuration = new ApiedConfig(this);
        Database.initialize();
        ApiedAPI.setPlugin(this);
    }

    @Override
    public void onDisable() {
        DB.close();
    }
}
