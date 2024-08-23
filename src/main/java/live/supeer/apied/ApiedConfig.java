package live.supeer.apied;

import lombok.Getter;
import org.bukkit.Location;

import java.util.List;

@Getter
public class ApiedConfig {

    private final String sqlHost;
    private final int sqlPort;
    private final String sqlDatabase;
    private final String sqlUsername;
    private final String sqlPassword;

    private final int startingBalance;

    private final int shopMaxItems;
    private final List<String> bannedPreviewItems;

    ApiedConfig(Apied plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        sqlHost = plugin.getConfig().getString("sql.host");
        sqlPort = plugin.getConfig().getInt("sql.port");
        sqlDatabase = plugin.getConfig().getString("sql.database");
        sqlUsername = plugin.getConfig().getString("sql.username");
        sqlPassword = plugin.getConfig().getString("sql.password");

        startingBalance = plugin.getConfig().getInt("settings.startingbalance");

        shopMaxItems = plugin.getConfig().getInt("settings.shop.maxitems");
        bannedPreviewItems = plugin.getConfig().getStringList("settings.shop.bannedpreviewitems");
    }

}
