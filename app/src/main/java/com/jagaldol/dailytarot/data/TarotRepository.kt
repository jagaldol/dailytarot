package com.jagaldol.dailytarot.data

import com.jagaldol.dailytarot.model.TodaySelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SaveError { LOAD, SAVE, WIDGET }

data class TodayState(
    val selection: TodaySelection = TodaySelection(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: SaveError? = null,
)

/** Application-owned, serialized writes survive Activity recreation. */
class TarotRepository(
    private val store: SelectionStore,
    scope: CoroutineScope,
    private val refreshWidgets: suspend () -> Unit,
) {
    private val mutableState = MutableStateFlow(TodayState())
    val state = mutableState.asStateFlow()
    // Only the latest pending selection matters. Null requests a safe reload after a read error.
    private val requests = Channel<TodaySelection?>(Channel.CONFLATED)

    init {
        requests.trySend(null)
        scope.launch {
            for (selection in requests) {
                if (selection == null) {
                    load()
                    continue
                }
                var error: SaveError? = null
                try {
                    store.save(selection)
                    try {
                        refreshWidgets()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        error = SaveError.WIDGET
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    error = SaveError.SAVE
                }
                mutableState.update { current ->
                    if (current.selection == selection) current.copy(saving = false, error = error)
                    else current
                }
            }
        }
    }

    private suspend fun load() {
        try {
            val selection = store.load()
            var error: SaveError? = null
            try {
                // Repairs an update interrupted after a previous successful save.
                refreshWidgets()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = SaveError.WIDGET
            }
            mutableState.value = TodayState(selection = selection, loading = false, error = error)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            mutableState.value = TodayState(error = SaveError.LOAD)
        }
    }

    fun selectCard(id: Int) = submit(state.value.selection.copy(cardId = id))

    fun setReversed(reversed: Boolean) = submit(state.value.selection.copy(reversed = reversed))

    private fun submit(selection: TodaySelection) {
        if (state.value.loading) return
        mutableState.update { it.copy(selection = selection, saving = true, error = null) }
        requests.trySend(selection)
    }

    fun retry() {
        if (state.value.loading) {
            mutableState.update { it.copy(error = null) }
            requests.trySend(null)
        } else {
            submit(state.value.selection)
        }
    }
}
