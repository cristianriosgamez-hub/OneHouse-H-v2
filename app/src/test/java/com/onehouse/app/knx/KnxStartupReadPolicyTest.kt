package com.onehouse.app.knx

import org.junit.Assert.*
import org.junit.Test

class KnxStartupReadPolicyTest {
    @Test fun hReaderExcludesBothEventOnlyAlarmsFromIts35Reads() {
        val excluded = com.onehouse.app.feature.h08.H08Safety.EVENT_ONLY
        assertEquals(setOf("15/0/13", "15/0/15"), excluded)
        val catalog = com.onehouse.knxlab.knx.LabCatalog.addresses.map { it.address }
        assertEquals(35, catalog.filterNot { it in excluded }.distinct().size)
    }
    private fun targets(vararg addresses: String) = addresses.associateWith {
        KnxLoadTarget(it, setOf("Vivienda"), setOf(KnxLoadKind.SENSOR), setOf(it))
    }
    private fun state(address: String) = KnxStateRepository.State(
        address, false, "0", "3.15.2",
        KnxConnectionManager.IncomingGroupTelegram.Kind.RESPONSE, "GroupValueResponse", Long.MAX_VALUE
    )

    @Test fun generalCommandsNeverBecomeReadsAndValveFeedbackIsDeduplicated() {
        val input = KnxStartupReadPolicy.commandOnly.toList() + listOf(" 2/4/1 ", "2/4/1", "1/2/1", "5/3/2", "15/0/15")
        assertEquals(listOf("2/4/1", "1/2/1", "5/3/2", "15/0/15"), KnxStartupReadPolicy.readable(input))
        assertTrue("15/0/15" in KnxStartupReadPolicy.tracked(input))
        assertFalse(KnxStartupReadPolicy.tracked(input).any { it in KnxStartupReadPolicy.commandOnly })
    }

    @Test fun missingValvePreventsOneHundredPercentEvenWhenRoundFinished() {
        val progress = KnxLoadProgressSnapshot(targets("1/2/1", "2/4/1", "15/0/15"),
            receivedAddresses = setOf("1/2/1"), noResponseAddresses = setOf("2/4/1"), finished = true)
        assertEquals(3, progress.total)
        assertEquals(33, progress.percent)
        assertTrue(progress.waitingEventAddresses.isEmpty())
        assertEquals(66, progress.copy(receivedAddresses = setOf("1/2/1", "2/4/1")).percent)
    }

    @Test fun rainIsAReadableStateAndCountsOnlyWhenActuallyReceived() {
        val progress = KnxLoadProgressSnapshot(targets("2/4/1", "15/0/15"), receivedAddresses = setOf("15/0/15"))
        assertEquals(50, progress.percent)
        assertEquals(1, progress.received)
        assertTrue(progress.waitingEventAddresses.isEmpty())
        assertEquals(0, KnxLoadProgressSnapshot().percent)
    }

    @Test fun noResponseDoesNotInventDryRainAndLateFeedbackReconciles() {
        KnxLoadProgressRepository.begin(targets("2/4/1", "15/0/15"))
        KnxLoadProgressRepository.finish(listOf("2/4/1", "15/0/15"), emptyMap())
        val missing = KnxLoadProgressRepository.progress.value
        assertEquals(0, missing.percent)
        assertEquals(setOf("2/4/1", "15/0/15"), missing.noResponseAddresses)
        assertTrue(missing.waitingEventAddresses.isEmpty())
        KnxLoadProgressRepository.reconcileAcceptedState(state("2/4/1"))
        assertEquals(50, KnxLoadProgressRepository.progress.value.percent)
        KnxLoadProgressRepository.reconcileAcceptedState(state("15/0/15"))
        assertTrue(KnxLoadProgressRepository.progress.value.noResponseAddresses.isEmpty())
        assertEquals(100, KnxLoadProgressRepository.progress.value.percent)
    }

    @Test fun omittedFailuresStillRemainMissingAndUnrelatedTelegramDoesNotCount() {
        KnxLoadProgressRepository.begin(targets("2/4/1"))
        KnxLoadProgressRepository.finish(emptyList(), mapOf("1/1/50" to state("1/1/50")))
        assertEquals(setOf("2/4/1"), KnxLoadProgressRepository.progress.value.noResponseAddresses)
        assertEquals(0, KnxLoadProgressRepository.progress.value.percent)
        KnxLoadProgressRepository.reconcileAcceptedState(state("1/1/50"))
        assertEquals(0, KnxLoadProgressRepository.progress.value.percent)
    }

    @Test fun persistedOldStateCannotCompleteCurrentSessionAndOtherSensorsRemainRequired() {
        assertTrue("5/4/1" in KnxStartupReadPolicy.readable(listOf("5/4/1", "15/0/15")))
        assertFalse(KnxStartupReadPolicy.isEvent("5/4/2"))
        assertFalse(KnxStartupReadPolicy.isEvent(KnxAddressBook.Terrace.RAINING))
        KnxLoadProgressRepository.begin(targets("2/4/1", "15/0/15"))
        KnxLoadProgressRepository.finish(emptyList(), mapOf("2/4/1" to state("2/4/1").copy(timestampMillis = 0)))
        assertEquals(0, KnxLoadProgressRepository.progress.value.percent)
        KnxLoadProgressRepository.syncReceived(mapOf("2/4/1" to state("2/4/1")))
        assertEquals(50, KnxLoadProgressRepository.progress.value.percent)
        KnxLoadProgressRepository.syncReceived(mapOf("2/4/1" to state("2/4/1"), "15/0/15" to state("15/0/15")))
        assertEquals(100, KnxLoadProgressRepository.progress.value.percent)
        assertTrue(KnxLoadProgressRepository.progress.value.noResponseAddresses.isEmpty())
        assertTrue(KnxLoadProgressRepository.progress.value.waitingEventAddresses.isEmpty())
    }
}
