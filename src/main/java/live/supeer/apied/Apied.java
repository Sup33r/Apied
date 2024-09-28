 package live.supeer.apied;

import co.aikar.idb.DB;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

 public final class Apied extends JavaPlugin {

    private static Apied plugin;
    public static Apied getInstance() {
        return plugin;
    }
    public static ApiedConfig configuration;
    private static LanguageManager languageManager;

    public static HashMap<Player, String> goSigns = new HashMap<>();
    public static HashMap<Player, Integer> signEdit = new HashMap<>();


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

     public static String getMessage(@NotNull String key, String... replacements) {
         String message = languageManager.getValue(key, "sv_se", replacements);

         if (message != null && !message.isEmpty()) {
             // Deserialize MiniMessage to a Component
             Component component = languageManager.getMiniMessage().deserialize(message);
             // Convert the Component to a legacy formatted string
             return LegacyComponentSerializer.legacySection().serialize(component);
         }
         return null;
     }

     public static Component getMessageComponent(@NotNull String key, String... replacements) {
         String message = languageManager.getValue(key, "sv_se", replacements);

         if (message != null && !message.isEmpty()) {
             return languageManager.getMiniMessage().deserialize(message);
         }
         return null;
     }

     public static Component stringToMiniMessage(String message) {
         return languageManager.getMiniMessage().deserialize(message);
     }

     public static String componentToMiniMessage(Component component) {
         return languageManager.getMiniMessage().serialize(component);
     }


     private static @NotNull String getLocale(@NotNull CommandSender sender) {
         if (sender instanceof Player) {
             return ((Player) sender).locale().toString();
         } else {
             return getInstance().getConfig().getString("settings.locale", "sv_se");
         }
     }
}
