 package live.supeer.apied;

import co.aikar.idb.DB;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

 public final class Apied extends JavaPlugin {

    private static Apied plugin;
    public static Apied getInstance() {
        return plugin;
    }
    public static ApiedConfig configuration;
    private static LanguageManager languageManager;



    @Override
    public void onEnable() {
        plugin = this;
        configuration = new ApiedConfig(this);
        languageManager = new LanguageManager(this, "sv_se");
        Database.initialize();
        ApiedAPI.setPlugin(this);
    }

    @Override
    public void onDisable() {
        DB.close();
    }

     public static void sendMessage(@NotNull CommandSender sender, @NotNull String key, String... replacements) {
         String message = languageManager.getValue(key, getLocale(sender), replacements);
         if (message != null && !message.isEmpty()) {
             Component component = languageManager.getMiniMessage().deserialize(message);
             sender.sendMessage(component);
         }
     }

     private static @NotNull String getLocale(@NotNull CommandSender sender) {
         if (sender instanceof Player) {
             return ((Player) sender).locale().toString();
         } else {
             return getInstance().getConfig().getString("settings.locale", "sv_se");
         }
     }
}
