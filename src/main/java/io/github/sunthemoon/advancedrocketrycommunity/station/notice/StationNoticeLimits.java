package io.github.sunthemoon.advancedrocketrycommunity.station.notice;

/** ADR-065 section 5.3 bounds of the station notices and their offline member-notice store. */
public final class StationNoticeLimits {
    /** Entries in the store, one per station: the station registry's own limit. */
    public static final int MAX_STATIONS = 4_096;
    /** Member acknowledgements per station; the owner's acknowledgement has its own field. */
    public static final int MAX_MEMBER_ACKNOWLEDGEMENTS = 32;
    /** The NBT payload of one stored outcome, its localized argument envelope. */
    public static final int MAX_ENVELOPE_BYTES = 512;
    public static final int ROOT_SCHEMA = 1;
    public static final int MAX_ROOT_DEPTH = 16;
    public static final int MAX_ROOT_BYTES = 8 * 1_024 * 1_024;
    /** Notices shown to one player at one login; the rest wait for a later login. */
    public static final int MAX_LOGIN_NOTICES = 16;

    private StationNoticeLimits() {
    }
}
