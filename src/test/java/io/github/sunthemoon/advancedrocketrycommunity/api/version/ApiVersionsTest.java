package io.github.sunthemoon.advancedrocketrycommunity.api.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiVersionsTest {
    @Test
    void currentVersionIncludesTheFrozenSuitEquipmentContract() {
        assertEquals(new ApiVersion(1, 3), ApiVersions.current());
    }

    @Test
    void currentHostSupportsPreviousAndCurrentMinorButRejectsFutureMinor() {
        assertEquals(ApiCompatibility.COMPATIBLE,
                ApiVersions.check(ApiVersions.current(), new ApiVersion(1, 0)));
        assertEquals(ApiCompatibility.COMPATIBLE,
                ApiVersions.check(ApiVersions.current(), new ApiVersion(1, 1)));
        assertEquals(ApiCompatibility.COMPATIBLE,
                ApiVersions.check(ApiVersions.current(), new ApiVersion(1, 2)));
        assertEquals(ApiCompatibility.COMPATIBLE,
                ApiVersions.check(ApiVersions.current(), new ApiVersion(1, 3)));
        assertEquals(ApiCompatibility.MINOR_TOO_OLD,
                ApiVersions.check(ApiVersions.current(), new ApiVersion(1, 4)));
    }

    @Test
    void equalAndNewerMinorSatisfyAnExactMajor() {
        assertEquals(ApiCompatibility.COMPATIBLE,
                ApiVersions.check(new ApiVersion(1, 0), new ApiVersion(1, 0)));
        assertEquals(ApiCompatibility.COMPATIBLE,
                ApiVersions.check(new ApiVersion(1, 12), new ApiVersion(1, 2)));
    }

    @Test
    void olderMinorRejectsTheRequirement() {
        assertEquals(ApiCompatibility.MINOR_TOO_OLD,
                ApiVersions.check(new ApiVersion(1, 1), new ApiVersion(1, 2)));
    }

    @Test
    void majorMismatchWinsInBothDirectionsRegardlessOfMinor() {
        assertEquals(ApiCompatibility.MAJOR_MISMATCH,
                ApiVersions.check(new ApiVersion(1, Integer.MAX_VALUE), new ApiVersion(2, 0)));
        assertEquals(ApiCompatibility.MAJOR_MISMATCH,
                ApiVersions.check(new ApiVersion(2, 0), new ApiVersion(1, Integer.MAX_VALUE)));
    }

    @Test
    void extremeValidIntegersDoNotOverflow() {
        ApiVersion maximum = new ApiVersion(Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(ApiCompatibility.COMPATIBLE, ApiVersions.check(maximum, maximum));
        assertEquals(ApiCompatibility.MINOR_TOO_OLD,
                ApiVersions.check(new ApiVersion(Integer.MAX_VALUE, 0), maximum));
        assertEquals(ApiCompatibility.COMPATIBLE,
                ApiVersions.check(maximum, new ApiVersion(Integer.MAX_VALUE, 0)));
        assertEquals(ApiCompatibility.MAJOR_MISMATCH,
                ApiVersions.check(maximum, new ApiVersion(1, 0)));
    }

    @Test
    void invalidVersionComponentsAreRejected() {
        for (int major : new int[]{Integer.MIN_VALUE, -1, 0}) {
            assertThrows(IllegalArgumentException.class, () -> new ApiVersion(major, 0));
        }
        for (int minor : new int[]{Integer.MIN_VALUE, -1}) {
            assertThrows(IllegalArgumentException.class, () -> new ApiVersion(1, minor));
        }
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> ApiVersions.check(null, ApiVersions.current()));
        assertThrows(NullPointerException.class, () -> ApiVersions.check(ApiVersions.current(), null));
        assertThrows(NullPointerException.class, () -> ApiVersions.check(null, null));
    }

    @Test
    void recordHasValueSemanticsAndAccessors() {
        ApiVersion version = new ApiVersion(2, 3);
        assertEquals(2, version.major());
        assertEquals(3, version.minor());
        assertEquals(version, new ApiVersion(2, 3));
        assertEquals(version.hashCode(), new ApiVersion(2, 3).hashCode());
        assertNotEquals(version, new ApiVersion(2, 4));
        assertNotEquals(version, new ApiVersion(3, 3));
    }
}
