package com.onehouse.app.feature.more

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KnxDiagnosticReadPolicyTest {
    private val events = emptySet<String>()

    @Test fun commandsWithoutFeedbackAreNotMissingStates() {
        for (address in listOf("1/1/1", "2/1/7", "5/2/1", "5/2/2", "5/2/4", "5/2/5")) {
            val policy = diagnosticReadPolicy("Mando/escritura", address, events)
            assertEquals(KnxDiagnosticReadPolicy.COMMAND, policy)
            assertFalse(policy.label(false).contains("SIN RESPUESTA"))
            assertFalse(policy.readWithoutValueMessage().contains("sin respuesta"))
        }
    }

    @Test fun rainIsDiagnosedAsReadableState() {
        for (kind in listOf("Lectura", "Lectura/estado")) {
            val policy = diagnosticReadPolicy(kind, "15/0/15", events)
            assertEquals(KnxDiagnosticReadPolicy.STATE, policy)
            assertEquals("○ SIN RESPUESTA", policy.label(false))
            assertEquals("● VALOR RECIBIDO", policy.label(true))
        }
    }

    @Test fun realStatesAndMixedReadWriteRemainVerifiable() {
        for (address in listOf("1/2/1", "2/2/9", "5/3/2", "5/3/4", "5/3/5", "5/2/6", "5/1/1", "5/4/1", "15/0/11", "15/0/13", "15/0/14")) {
            val policy = diagnosticReadPolicy("Lectura/estado", address, events)
            assertEquals(KnxDiagnosticReadPolicy.STATE, policy)
            assertEquals("○ SIN RESPUESTA", policy.label(false))
        }
        assertEquals(KnxDiagnosticReadPolicy.STATE,
            diagnosticReadPolicy("Lectura y mando", "5/5/1", events))
    }

    @Test fun receivedValueDoesNotClaimItWasAReadResponse() {
        for (policy in KnxDiagnosticReadPolicy.values()) {
            assertEquals("● VALOR RECIBIDO", policy.label(true))
        }
    }
}
