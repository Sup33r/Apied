package live.supeer.apied;

import lombok.Getter;

@Getter
public class ChestType {

    public static final ChestType SINGLE = new ChestType("single");
    public static final ChestType DOUBLE = new ChestType("double");
    public static final ChestType BARREL = new ChestType("barrel");
    public static final ChestType SHULKER = new ChestType("shulker");

    private final String type;

    public ChestType(String type) {
        this.type = type;
    }

    public static ChestType getByName(String type) {
        return switch (type) {
            case "single" -> SINGLE;
            case "double" -> DOUBLE;
            case "barrel" -> BARREL;
            case "shulker" -> SHULKER;
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }
}
