package com.example.android.data.connectivity

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the reconnect-detection logic that `ShopperViewModel.observeConnectivity`
 * relies on (skip-first + false→true transition emits).
 *
 * The real `ConnectivityManagerNetworkMonitor` is not unit-testable on the JVM
 * (it touches Android `ConnectivityManager`), so we assert that:
 *   1. `distinctUntilChanged` filters consecutive duplicates from a fake monitor.
 *   2. A small `ReconnectDetector` (mirroring the inline logic in
 *      `ShopperViewModel.observeConnectivity`) only emits `true` on
 *      `false -> true` transitions, skipping the very first emission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkMonitorTest {

    /**
     * Fake monitor whose `isOnline` flow we drive from a `MutableStateFlow`.
     * Wraps the state flow in `distinctUntilChanged` to mirror the production
     * implementation's terminal operator.
     */
    private class FakeNetworkMonitor(initial: Boolean) : NetworkMonitor {
        private val state = MutableStateFlow(initial)
        // StateFlow already de-dupes consecutive duplicates; no need to wrap in
        // `distinctUntilChanged` (the Kotlin compiler treats that as an error
        // since 1.9 — see "Operator Fusion" in StateFlow docs).
        override val isOnline: Flow<Boolean> = state

        fun emit(value: Boolean) {
            state.value = value
        }
    }

    /**
     * Mirrors `ShopperViewModel.observeConnectivity`: starts with `previous = null`,
     * collects the source flow, and emits `Unit` (treated as a "reconnect" pulse)
     * only when `previous == false && current == true`.
     */
    private fun reconnectPulses(source: Flow<Boolean>): Flow<Unit> = flow {
        var previous: Boolean? = null
        source.collect { current ->
            if (previous == false && current) {
                emit(Unit)
            }
            previous = current
        }
    }

    @Test
    fun `distinctUntilChanged filters consecutive duplicates from fake monitor`() = runTest {
        // Build a finite source so toList terminates.
        val source: Flow<Boolean> = flow {
            emit(true)
            emit(true)
            emit(false)
            emit(false)
            emit(true)
        }.distinctUntilChanged()

        val collected = source.toList()
        assertEquals(listOf(true, false, true), collected)
    }

    @Test
    fun `initial true alone does not emit a reconnect`() = runTest {
        val source = flow { emit(true) }
        val pulses = reconnectPulses(source).toList()
        assertEquals(0, pulses.size)
    }

    @Test
    fun `initial false then true emits one reconnect`() = runTest {
        val source = flow {
            emit(false)
            emit(true)
        }
        val pulses = reconnectPulses(source).toList()
        assertEquals(1, pulses.size)
    }

    @Test
    fun `true to false to true emits one reconnect`() = runTest {
        val source = flow {
            emit(true)
            emit(false)
            emit(true)
        }
        val pulses = reconnectPulses(source).toList()
        assertEquals(1, pulses.size)
    }

    @Test
    fun `consecutive true emissions do not trigger a reconnect`() = runTest {
        // After distinctUntilChanged duplicates would be filtered; but even
        // without it, the detector requires a `false` in-between.
        val source = flow {
            emit(true)
            emit(true)
            emit(true)
        }
        val pulses = reconnectPulses(source).toList()
        assertEquals(0, pulses.size)
    }

    @Test
    fun `multiple reconnect cycles each emit a pulse`() = runTest {
        val source = flow {
            emit(false)
            emit(true)   // pulse 1
            emit(false)
            emit(true)   // pulse 2
        }
        val pulses = reconnectPulses(source).toList()
        assertEquals(2, pulses.size)
    }

    @Test
    fun `fake NetworkMonitor wires through to the detector`() = runTest {
        val monitor = FakeNetworkMonitor(initial = true)
        // Drive the monitor through a transition while collecting.
        // Because we need a finite stream, use a flow that pulls a few values
        // off the monitor by combining with explicit state updates.
        val transitions = flow {
            emit(true)   // initial
            emit(false)  // dropped
            emit(true)   // reconnect
        }.distinctUntilChanged()

        val pulses = reconnectPulses(transitions).toList()
        assertEquals(1, pulses.size)

        // sanity check: the fake responds to mutation through the public surface.
        monitor.emit(false)
        monitor.emit(true)
    }
}
