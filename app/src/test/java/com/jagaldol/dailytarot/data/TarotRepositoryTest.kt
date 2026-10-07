package com.jagaldol.dailytarot.data

import com.jagaldol.dailytarot.model.TodaySelection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TarotRepositoryTest {
    private class FakeStore(var selection: TodaySelection = TodaySelection()) : SelectionStore {
        var failLoad = false
        var failSave = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun load(): TodaySelection {
            if (failLoad) throw IOException("read failed")
            return selection
        }
        override suspend fun save(selection: TodaySelection) {
            gate?.await()
            if (failSave) throw IOException("write failed")
            this.selection = selection
        }
    }

    @Test
    fun rapidSelectionsKeepTheFinalCardAndOrientationTogether() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = FakeStore(TodaySelection(4, false))
            var widgetValue: TodaySelection? = null
            val repository = TarotRepository(store, scope) { widgetValue = store.selection }
            val gate = CompletableDeferred<Unit>()
            store.gate = gate
            repository.selectCard(1)
            repository.setReversed(true)
            repository.selectCard(77)
            repository.setReversed(false)
            repository.setReversed(true)
            assertTrue(repository.state.value.saving)
            gate.complete(Unit)
            withTimeout(2_000) { repository.state.first { !it.saving } }
            assertEquals(TodaySelection(77, true), store.selection)
            assertEquals(store.selection, widgetValue)
            assertEquals(store.selection, repository.state.value.selection)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failedReadCannotOverwriteExistingDataAndCanBeRetried() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = FakeStore(TodaySelection(19, true)).apply { failLoad = true }
            val repository = TarotRepository(store, scope) {}
            repository.selectCard(0)
            assertEquals(SaveError.LOAD, repository.state.value.error)
            assertEquals(TodaySelection(19, true), store.selection)
            store.failLoad = false
            repository.retry()
            assertFalse(repository.state.value.loading)
            assertEquals(store.selection, repository.state.value.selection)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failedSaveShowsAnErrorAndRetryPersistsTheRequestedSelection() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = FakeStore().apply { failSave = true }
            val repository = TarotRepository(store, scope) {}
            repository.selectCard(21)
            assertEquals(SaveError.SAVE, repository.state.value.error)
            assertEquals(TodaySelection(), store.selection)
            store.failSave = false
            repository.retry()
            assertEquals(TodaySelection(21), store.selection)
            assertEquals(null, repository.state.value.error)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun widgetFailureDoesNotLoseSavedCardAndCanBeRetried() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = FakeStore()
            var failWidget = false
            val repository = TarotRepository(store, scope) { if (failWidget) error("enqueue failed") }
            failWidget = true
            repository.selectCard(10)
            assertEquals(TodaySelection(10), store.selection)
            assertEquals(SaveError.WIDGET, repository.state.value.error)
            failWidget = false
            repository.retry()
            assertEquals(null, repository.state.value.error)
        } finally {
            scope.cancel()
        }
    }
}
