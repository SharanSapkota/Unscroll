package com.unscroll.app.data.plus

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.unscroll.app.data.plus.FakeBillingGateway.Companion.plus
import com.unscroll.app.domain.plus.PlusCheck
import com.unscroll.app.domain.plus.PurchaseState
import com.unscroll.app.domain.plus.PurchaseUpdate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EntitlementRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val billing = FakeBillingGateway()
    private lateinit var preferences: EntitlementPreferences

    private fun TestScope.repository(debugBuild: Boolean = false): EntitlementRepository {
        preferences = EntitlementPreferences(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                tempFolder.root.resolve("plus.preferences_pb")
            },
        )
        return EntitlementRepository(billing, preferences, backgroundScope, debugBuild)
    }

    @Test
    fun freshInstall_isFree_withoutAnEndedNotice() = runTest {
        val repository = repository()
        assertFalse(repository.isPlus.first())

        assertEquals(PlusCheck.FREE, repository.check())
        assertFalse(repository.isPlus.first())
        assertFalse(repository.plusEnded.first())
    }

    @Test
    fun purchase_becomesPlus_andIsAcknowledged() = runTest {
        val repository = repository()
        billing.purchases = listOf(plus(acknowledged = false, token = "new"))

        assertEquals(PlusCheck.PLUS, repository.check())
        assertTrue(repository.isPlus.first())
        assertEquals(listOf("new"), billing.acknowledged)

        // Already acknowledged: not again.
        repository.check()
        assertEquals(listOf("new"), billing.acknowledged)
    }

    @Test
    fun failedAcknowledgement_stillPlus_andRetriedNextTime() = runTest {
        val repository = repository()
        billing.purchases = listOf(plus(acknowledged = false, token = "new"))
        billing.acknowledgeSucceeds = false

        assertEquals(PlusCheck.PLUS, repository.check())
        assertTrue(repository.isPlus.first())

        billing.acknowledgeSucceeds = true
        repository.check()
        assertEquals(listOf("new"), billing.acknowledged)
    }

    @Test
    fun pending_isNotPlus() = runTest {
        val repository = repository()
        billing.purchases = listOf(plus(state = PurchaseState.PENDING, acknowledged = false))

        assertEquals(PlusCheck.PENDING, repository.check())
        assertFalse(repository.isPlus.first())
        assertTrue(repository.pending.value)
        assertEquals(emptyList<String>(), billing.acknowledged)

        // The payment arrives.
        billing.purchases = listOf(plus(acknowledged = false))
        assertEquals(PlusCheck.PLUS, repository.check())
        assertFalse(repository.pending.value)
    }

    @Test
    fun billingUnavailable_keepsTheLastKnownState() = runTest {
        val repository = repository()
        billing.purchases = listOf(plus())
        repository.check()

        billing.purchases = null
        assertEquals(PlusCheck.UNAVAILABLE, repository.check())
        assertTrue(repository.isPlus.first())
        assertFalse(repository.plusEnded.first())
    }

    @Test
    fun cancelledOrExpired_endsPlus_withTheNotice_untilResubscribed() = runTest {
        val repository = repository()
        billing.purchases = listOf(plus())
        repository.check()

        // Expired (or on account hold): Play no longer returns it.
        billing.purchases = emptyList()
        assertEquals(PlusCheck.FREE, repository.check())
        assertFalse(repository.isPlus.first())
        assertTrue(repository.plusEnded.first())

        repository.dismissPlusEnded()
        assertFalse(repository.plusEnded.first())

        billing.purchases = listOf(plus())
        repository.check()
        assertTrue(repository.isPlus.first())
        assertFalse(repository.plusEnded.first())
    }

    @Test
    fun resubscribing_clearsAnUndismissedNotice() = runTest {
        val repository = repository()
        billing.purchases = listOf(plus())
        repository.check()
        billing.purchases = emptyList()
        repository.check()
        assertTrue(repository.plusEnded.first())

        billing.purchases = listOf(plus())
        repository.check()
        assertFalse(repository.plusEnded.first())
    }

    @Test
    fun gracePeriod_staysPlus() = runTest {
        // During a grace period Play still returns the purchase as PURCHASED.
        val repository = repository()
        billing.purchases = listOf(plus())
        repository.check()
        repository.check()
        assertTrue(repository.isPlus.first())
        assertFalse(repository.plusEnded.first())
    }

    @Test
    fun purchaseUpdate_triggersACheck() = runTest {
        val repository = repository()
        repository.start()
        runCurrent()
        assertFalse(repository.isPlus.first())

        billing.purchases = listOf(plus(acknowledged = false))
        billing.updates.emit(PurchaseUpdate.Changed)

        assertTrue(repository.isPlus.first { it })
        assertEquals(listOf("token"), billing.acknowledged)
    }

    @Test
    fun cancelledPurchaseFlow_changesNothing() = runTest {
        val repository = repository()
        repository.start()
        runCurrent()
        billing.purchases = listOf(plus())
        billing.updates.emit(PurchaseUpdate.Cancelled)
        runCurrent()

        assertFalse(repository.isPlus.first())
    }

    @Test
    fun restore_findsTheSubscription() = runTest {
        val repository = repository()
        billing.purchases = listOf(plus())
        assertEquals(PlusCheck.PLUS, repository.restore())
        assertTrue(repository.isPlus.first())
    }

    @Test
    fun debugOverride_onlyInDebugBuilds() = runTest {
        val release = repository(debugBuild = false)
        release.setDebugOverride(true)
        assertFalse(release.isPlus.first())
        assertNull(release.debugOverride.first())
        assertNull(preferences.debugOverride.first())
    }

    @Test
    fun debugOverride_forcesBothWays_andForcingOffShowsTheNotice() = runTest {
        val repository = repository(debugBuild = true)
        repository.setDebugOverride(true)
        assertTrue(repository.isPlus.first())

        repository.setDebugOverride(false)
        assertFalse(repository.isPlus.first())
        assertTrue(repository.plusEnded.first())

        billing.purchases = listOf(plus())
        repository.check()
        // Still forced off; null follows Billing again.
        assertFalse(repository.isPlus.first())
        repository.setDebugOverride(null)
        assertTrue(repository.isPlus.first())
    }
}
