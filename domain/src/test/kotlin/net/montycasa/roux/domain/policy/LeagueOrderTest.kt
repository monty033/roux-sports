package net.montycasa.roux.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class LeagueOrderTest {
    @Test fun unknownPersistedIdIsDropped() {
        assertEquals(listOf("mlb", "nhl", "nfl"), LeagueOrder.reconcile(listOf("mlb", "gone", "nhl", "nfl")))
    }

    @Test fun missingCanonicalIdsAreAppendedInCanonicalOrder() {
        assertEquals(listOf("mlb", "nhl", "nfl"), LeagueOrder.reconcile(listOf("mlb")))
    }

    @Test fun emptyPersistedOrderYieldsCanonicalOrder() {
        assertEquals(listOf("nhl", "nfl", "mlb"), LeagueOrder.reconcile(emptyList()))
    }

    @Test fun duplicatesAreCollapsedWithoutChangingSavedRelativeOrder() {
        assertEquals(listOf("nfl", "nhl", "mlb"), LeagueOrder.reconcile(listOf("nfl", "nhl", "nfl", "mlb", "nhl")))
    }

    @Test fun catalogChangesPreserveSurvivorsAndAppendNewIdsOnce() {
        assertEquals(listOf("mlb", "new"), LeagueOrder.reconcile(listOf("nhl", "mlb"), listOf("new", "mlb", "new")))
    }
}
