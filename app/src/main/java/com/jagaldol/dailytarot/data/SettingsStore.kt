package com.jagaldol.dailytarot.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jagaldol.dailytarot.model.TodaySelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.TimeZone

// Backed up with the readings.
private val Context.settingsStore by preferencesDataStore(name = "settings")

// Device-specific folder grants; excluded from backups and re-linked after a restore.
private val Context.connectionStore by preferencesDataStore(name = "lifebase_connection")

private val zoneKey = stringPreferencesKey("zone_id")
private val legacyMigratedKey = booleanPreferencesKey("legacy_migrated")
private val revealedDayKey = stringPreferencesKey("revealed_day")
private val autoDrawKey = booleanPreferencesKey("auto_draw")
private val widgetPromptNeverKey = booleanPreferencesKey("widget_prompt_never")
private val widgetPromptAskedDayKey = stringPreferencesKey("widget_prompt_asked_day")
private val dayStartKey = intPreferencesKey("day_start_minutes")
private val autoDrawMinutesKey = intPreferencesKey("auto_draw_minutes")

/** Midnight: a new day shows its own card (or a face-down one) right away. */
const val DEFAULT_DAY_START_MINUTES = 0

/** 08:00: when automatic drawing, if enabled, picks today's card. */
const val DEFAULT_AUTO_DRAW_MINUTES = 8 * 60

private val treeUriKey = stringPreferencesKey("tree_uri")
private val journalDocKey = stringPreferencesKey("journal_document_id")
private val labelKey = stringPreferencesKey("label")
private val connectionIdKey = stringPreferencesKey("connection_id")
private val syncStateKey = stringPreferencesKey("sync_state")
private val syncAtKey = longPreferencesKey("sync_at")
private val syncMessageKey = stringPreferencesKey("sync_message")
private val fingerprintsKey = stringPreferencesKey("file_fingerprints")

data class LifebaseConnection(
    val treeUri: String,
    val journalDocumentId: String,
    val label: String,
    /** A new id per connection; late results from an older connection are discarded. */
    val id: String,
)

enum class SyncState { NEVER, UPDATED, WAITING, ERROR, PERMISSION_LOST }

data class SyncStatus(val state: SyncState = SyncState.NEVER, val checkedAt: Long = 0, val message: String? = null)

data class AppSettings(
    val zoneId: String? = null,
    val revealedDay: String? = null,
    val autoDraw: Boolean = true,
    /** "다시 보지 않기": never suggest the home screen widget again. */
    val widgetPromptNever: Boolean = false,
    /** The date the widget suggestion was last answered; it is asked at most once per card day. */
    val widgetPromptAskedDay: String? = null,
    val dayStartMinutes: Int = DEFAULT_DAY_START_MINUTES,
    val autoDrawMinutes: Int = DEFAULT_AUTO_DRAW_MINUTES,
    val connection: LifebaseConnection? = null,
    val sync: SyncStatus = SyncStatus(),
)

/** What [ReadingRepository] needs from settings; faked in unit tests. */
interface ReadingSettings {
    suspend fun zoneId(): String

    /** The pre-history card selection, or null once migrated or if none was made. */
    suspend fun legacySelection(): TodaySelection?

    suspend fun markLegacyMigrated()

    suspend fun connectionId(): String?

    /** Whether the app draws a card by itself once [autoDrawMinutes] has passed. */
    suspend fun autoDraw(): Boolean

    /** Minutes after local midnight when the new day takes over from yesterday's card. */
    suspend fun dayStartMinutes(): Int

    /** Minutes after local midnight when automatic drawing picks today's card. */
    suspend fun autoDrawMinutes(): Int
}

class SettingsStore(context: Context) : ReadingSettings {
    private val settings = context.applicationContext.settingsStore
    private val connection = context.applicationContext.connectionStore
    private val legacy = TarotStore(context)

    val state: Flow<AppSettings> = combine(settings.data, connection.data) { s, c ->
        AppSettings(
            zoneId = s[zoneKey],
            revealedDay = s[revealedDayKey],
            autoDraw = s[autoDrawKey] ?: true,
            widgetPromptNever = s[widgetPromptNeverKey] ?: false,
            widgetPromptAskedDay = s[widgetPromptAskedDayKey],
            dayStartMinutes = s[dayStartKey] ?: DEFAULT_DAY_START_MINUTES,
            autoDrawMinutes = s[autoDrawMinutesKey] ?: DEFAULT_AUTO_DRAW_MINUTES,
            connection = c.toConnection(),
            sync = SyncStatus(
                state = enumOrNull<SyncState>(c[syncStateKey]) ?: SyncState.NEVER,
                checkedAt = c[syncAtKey] ?: 0,
                message = c[syncMessageKey],
            ),
        )
    }

    override suspend fun zoneId(): String {
        settings.data.first()[zoneKey]?.let { return it }
        var zone = TimeZone.getDefault().id
        // First run pins the device zone so later travel does not move saved dates.
        settings.edit { prefs -> zone = prefs[zoneKey] ?: zone.also { prefs[zoneKey] = it } }
        return zone
    }

    suspend fun setZoneId(zoneId: String) {
        settings.edit { it[zoneKey] = zoneId }
    }

    override suspend fun autoDraw(): Boolean = settings.data.first()[autoDrawKey] ?: true

    override suspend fun dayStartMinutes(): Int = settings.data.first()[dayStartKey] ?: DEFAULT_DAY_START_MINUTES

    override suspend fun autoDrawMinutes(): Int = settings.data.first()[autoDrawMinutesKey] ?: DEFAULT_AUTO_DRAW_MINUTES

    /** Records an answer to the widget suggestion for [day]; [never] stops asking for good. */
    suspend fun answerWidgetPrompt(day: String, never: Boolean) {
        settings.edit {
            it[widgetPromptAskedDayKey] = day
            if (never) it[widgetPromptNeverKey] = true
        }
    }

    suspend fun setAutoDraw(enabled: Boolean) {
        settings.edit { it[autoDrawKey] = enabled }
    }

    suspend fun setDayStartMinutes(minutes: Int) {
        settings.edit { it[dayStartKey] = minutes.coerceIn(0, 24 * 60 - 1) }
    }

    suspend fun setAutoDrawMinutes(minutes: Int) {
        settings.edit { it[autoDrawMinutesKey] = minutes.coerceIn(0, 24 * 60 - 1) }
    }

    /** Clears the revealed mark of a deleted reading, so its replacement turns over again. */
    suspend fun forgetRevealed(day: String) {
        settings.edit { if (it[revealedDayKey] == day) it.remove(revealedDayKey) }
    }

    suspend fun markRevealed(day: String) {
        settings.edit { it[revealedDayKey] = day }
    }

    override suspend fun legacySelection(): TodaySelection? {
        if (settings.data.first()[legacyMigratedKey] == true) return null
        return legacy.load().takeIf { it.cardId != null }
    }

    override suspend fun markLegacyMigrated() {
        if (settings.data.first()[legacyMigratedKey] != true) settings.edit { it[legacyMigratedKey] = true }
    }

    override suspend fun connectionId(): String? = connection.data.first()[connectionIdKey]

    suspend fun connection(): LifebaseConnection? = connection.data.first().toConnection()

    suspend fun connect(value: LifebaseConnection) {
        connection.edit {
            it.clear()
            it[treeUriKey] = value.treeUri
            it[journalDocKey] = value.journalDocumentId
            it[labelKey] = value.label
            it[connectionIdKey] = value.id
        }
    }

    suspend fun disconnect() {
        connection.edit { it.clear() }
    }

    /** Ignored when the connection changed while the check was running. */
    suspend fun setSync(connectionId: String, status: SyncStatus) {
        connection.edit {
            if (it[connectionIdKey] != connectionId) return@edit
            it[syncStateKey] = status.state.name
            it[syncAtKey] = status.checkedAt
            status.message?.let { message -> it[syncMessageKey] = message } ?: it.remove(syncMessageKey)
        }
    }

    /** Last read size/mtime per recent note, so background checks can skip untouched files. */
    suspend fun fingerprints(): Map<String, String> =
        decodeFingerprints(connection.data.first()[fingerprintsKey])

    suspend fun setFingerprints(connectionId: String, values: Map<String, String>) {
        connection.edit {
            if (it[connectionIdKey] == connectionId) it[fingerprintsKey] = encodeFingerprints(values)
        }
    }

    private fun Preferences.toConnection(): LifebaseConnection? {
        return LifebaseConnection(
            treeUri = this[treeUriKey] ?: return null,
            journalDocumentId = this[journalDocKey] ?: return null,
            label = this[labelKey].orEmpty(),
            id = this[connectionIdKey] ?: return null,
        )
    }
}

internal fun encodeFingerprints(values: Map<String, String>): String =
    values.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }

internal fun decodeFingerprints(text: String?): Map<String, String> =
    text.orEmpty().split(';').mapNotNull { part ->
        val key = part.substringBefore('=', "")
        val value = part.substringAfter('=', "")
        if (key.isEmpty() || value.isEmpty()) null else key to value
    }.toMap()
