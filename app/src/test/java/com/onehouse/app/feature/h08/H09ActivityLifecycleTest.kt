package com.onehouse.app.feature.h08

import org.junit.Assert.*
import org.junit.Test

/** Lifecycle and session doubles. Device callbacks and HyperOS timing need device validation. */
class H09ActivityLifecycleTest {
    private class Fixture {
        data class Pending(val deadline: Long, val callback: () -> Unit, var cancelled: Boolean = false)
        var now = 0L
        var connects = 1
        var closes = 0
        var active = true
        var applied = 0
        var keepalives = 0
        val session = Any()
        val originalDeadline = 90_000L
        val tasks = mutableListOf<Pending>()
        var token = Any()
        val policy = H09ActivityLifecycle(::stop) { delay, action ->
            val task = Pending(now + delay, action)
            tasks.add(task)
            val cancel: () -> Unit = { task.cancelled = true }
            cancel
        }
        init {
            assertTrue(policy.consumeInitialLaunch(false))
            policy.started(token)
        }
        fun stop() { if (active) { active = false; closes++ } }
        fun advance(ms: Long) {
            now += ms
            tasks.filter { !it.cancelled && it.deadline <= now }.toList().forEach {
                it.cancelled = true
                it.callback()
            }
            if (now >= originalDeadline) stop()
        }
        fun event() { if (active) applied++ }
        fun rotate() {
            policy.stopped(token, true)
            token = Any()
            assertFalse(policy.consumeInitialLaunch(true))
            policy.started(token)
        }
        fun assertSameSession() {
            assertTrue(active)
            assertEquals(1, connects)
            assertEquals(0, closes)
            assertEquals(90_000L, originalDeadline)
        }
    }

    @Test fun activeWithoutRotationReachesOriginalDeadline() {
        val f = Fixture()
        f.advance(89_999); f.assertSameSession()
        f.advance(1); assertEquals(1, f.closes)
    }
    @Test fun portraitLandscapePortraitAndThreeConsecutiveRotations() {
        val f = Fixture()
        val original = f.session
        repeat(3) { f.rotate(); f.advance(400); f.assertSameSession(); assertSame(original, f.session) }
        f.advance(2_000); f.assertSameSession()
    }
    @Test fun eventsContinueAcrossReplacementAndKeepTheirValues() {
        val f = Fixture()
        f.event(); f.policy.stopped(f.token, true)
        f.event(); f.advance(500)
        f.token = Any(); f.policy.started(f.token); f.event()
        assertEquals(3, f.applied); f.assertSameSession()
    }
    @Test fun rotationDuringKeepaliveDoesNotRestartSession() {
        val f = Fixture()
        f.advance(30_000); f.keepalives++
        f.rotate(); f.keepalives++
        assertEquals(2, f.keepalives); f.assertSameSession()
    }
    @Test fun rotationNearNinetySecondsDoesNotExtendDeadline() {
        val f = Fixture()
        f.advance(89_800); f.rotate(); f.advance(200)
        assertEquals(1, f.closes); assertEquals(1, f.connects)
    }
    @Test fun manualStopDuringRotationWinsOverReplacement() {
        val f = Fixture()
        f.policy.stopped(f.token, true); f.stop()
        f.event(); f.token = Any(); f.policy.started(f.token); f.event()
        f.advance(2_000)
        assertFalse(f.active); assertEquals(1, f.closes); assertEquals(0, f.applied)
    }
    @Test fun manualStopImmediatelyAfterRotation() {
        val f = Fixture()
        f.rotate(); f.stop(); f.event()
        assertFalse(f.active); assertEquals(1, f.closes); assertEquals(0, f.applied)
    }
    @Test fun homeAndOtherAppStopImmediatelyWithoutWaitingForGrace() {
        repeat(2) {
            val f = Fixture()
            f.policy.stopped(f.token, false)
            assertFalse(f.active); assertEquals(1, f.closes); assertEquals(0L, f.now)
        }
    }
    @Test fun screenLockDuringReplacementStopsImmediately() {
        val f = Fixture()
        f.policy.stopped(f.token, true); f.policy.screenOff(); f.event()
        assertFalse(f.active); assertEquals(0, f.applied)
        f.policy.started(Any()); assertEquals(1, f.connects)
    }
    @Test fun backgroundReturnAndFreshActivityDoNotAutoStartReadOrMonitor() {
        val f = Fixture()
        f.policy.stopped(f.token, false)
        assertFalse(f.policy.consumeInitialLaunch(false))
        f.policy.started(Any())
        assertFalse(f.active); assertEquals(1, f.connects)
    }
    @Test fun restoredProcessDoesNotStartAutomatically() {
        var stops = 0
        val policy = H09ActivityLifecycle({ stops++ }, { _, _ -> {} })
        assertFalse(policy.consumeInitialLaunch(true))
        assertFalse(policy.consumeInitialLaunch(false))
        policy.started(Any())
        assertEquals(0, stops)
    }
    @Test fun missingReplacementHasBoundedGrace() {
        val f = Fixture()
        f.policy.stopped(f.token, true)
        f.advance(999); f.assertSameSession()
        f.advance(1); assertFalse(f.active); assertEquals(1, f.closes)
    }
    @Test fun staleTimeoutCannotStopAReplacementOrItsNextManualSession() {
        val f = Fixture()
        f.policy.stopped(f.token, true)
        val oldTimeout = f.tasks.single()
        f.policy.started(Any())
        oldTimeout.callback() // Even if callback cancellation lost a dispatch race.
        f.assertSameSession()
    }
    @Test fun oldInstanceStopCannotCloseAlreadyVisibleReplacement() {
        val f = Fixture()
        val newToken = Any()
        f.policy.started(newToken)
        f.policy.stopped(f.token, true)
        f.advance(2_000); f.assertSameSession()
        f.policy.stopped(newToken, false)
        assertEquals(1, f.closes)
    }
    @Test fun closeAndManualStopDuringRotationRemainIdempotent() {
        val f = Fixture()
        f.policy.stopped(f.token, true)
        f.stop() // Existing receiver terminates (deadline, sequence failure or DISCONNECT).
        f.stop() // User STOP.
        f.policy.screenOff(); f.policy.started(Any()); f.advance(2_000)
        assertEquals(1, f.closes); assertEquals(1, f.connects)
        assertFalse(f.policy.consumeInitialLaunch(true))
    }
}
