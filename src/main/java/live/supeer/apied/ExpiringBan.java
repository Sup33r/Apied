package live.supeer.apied;

import lombok.Getter;

import java.util.UUID;

@Getter
public class ExpiringBan {
    private UUID playerUUID;
    private long unbanTime;

    public ExpiringBan(UUID playerUUID, long unbanTime) {
        this.playerUUID = playerUUID;
        this.unbanTime = unbanTime;
    }
}
