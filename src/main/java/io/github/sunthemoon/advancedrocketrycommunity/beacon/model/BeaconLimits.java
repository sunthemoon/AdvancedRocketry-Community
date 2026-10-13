package io.github.sunthemoon.advancedrocketrycommunity.beacon.model;

/** ADR-065 section 7 bounds of the planetary beacon index and the finder's marker stream. */
public final class BeaconLimits {
    /** Records per celestial body. */
    public static final int MAX_PER_BODY = 256;
    /** Records per owner; also the most records one finder lookup reads. */
    public static final int MAX_PER_OWNER = 32;
    /** Records in the whole index. */
    public static final int MAX_TOTAL = 4_096;
    /** The encoded index root, in bytes of its NBT payload. */
    public static final int MAX_ROOT_BYTES = 1 << 20;
    /** A beacon name, in UTF-16 units, as the frozen marker codec bounds it. */
    public static final int MAX_NAME_CHARS = 32;
    /** A body or Level ResourceLocation, in characters. */
    public static final int MAX_ID_CHARS = 128;
    /** Markers in one snapshot, as the frozen marker codec bounds them. */
    public static final int MAX_MARKERS = 8;
    /** The finder updates a player at most once per this many server ticks. */
    public static final int UPDATE_INTERVAL_TICKS = 20;
    /** World limits of a beacon position: {@code Level.MAX_LEVEL_SIZE} and the dimension-type height range. */
    public static final int MAX_HORIZONTAL = 30_000_000;
    public static final int MIN_Y = -2_032;
    public static final int MAX_Y = 2_031;

    private BeaconLimits() {
    }

    /** At most {@link #MAX_NAME_CHARS} units, no control or formatting character and no unpaired surrogate. */
    public static boolean validName(String name) {
        if (name == null || name.length() > MAX_NAME_CHARS) {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            char value = name.charAt(index);
            if (Character.isISOControl(value) || value == '§') {
                return false;
            }
            if (Character.isHighSurrogate(value)) {
                if (index + 1 >= name.length() || !Character.isLowSurrogate(name.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(value)) {
                return false;
            }
        }
        return true;
    }

    /** An explicit {@code namespace:path} ResourceLocation of at most {@link #MAX_ID_CHARS} characters. */
    public static boolean validId(String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_ID_CHARS) {
            return false;
        }
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1 || id.indexOf(':', colon + 1) >= 0) {
            return false;
        }
        for (int index = 0; index < id.length(); index++) {
            char value = id.charAt(index);
            boolean common = value >= 'a' && value <= 'z' || value >= '0' && value <= '9' || value == '_'
                    || value == '-' || value == '.';
            if (!common && !(index > colon && value == '/') && index != colon) {
                return false;
            }
        }
        return true;
    }

    public static boolean validPosition(int x, int y, int z) {
        return Math.abs((long) x) <= MAX_HORIZONTAL && Math.abs((long) z) <= MAX_HORIZONTAL && y >= MIN_Y && y <= MAX_Y;
    }
}
