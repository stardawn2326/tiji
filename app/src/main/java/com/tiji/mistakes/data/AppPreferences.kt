package com.tiji.mistakes.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.tijiDataStore by preferencesDataStore("tiji_preferences")

data class AiProfile(
    val id: String,
    val name: String,
    val endpoint: String,
    val model: String
)

/** A vision-only helper bound to one text-model profile. The API key is kept in the
 * existing encrypted key store; keyProfileId lets a new helper reuse the text
 * profile's credential until the user enters a separate one. */
data class AiVisualProfile(
    val id: String,
    val name: String,
    val endpoint: String,
    val model: String,
    val textProfileId: String,
    val keyProfileId: String? = null
)

class AppPreferences(private val context: Context) {
    private val darkKey = booleanPreferencesKey("dark_theme")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val themePaletteKey = stringPreferencesKey("theme_palette")
    private val aiEndpointKey = stringPreferencesKey("ai_endpoint")
    private val aiModelKey = stringPreferencesKey("ai_model")
    private val aiProfilesKey = stringPreferencesKey("ai_profiles")
    private val activeAiProfileKey = stringPreferencesKey("active_ai_profile")
    private val aiVisualProfilesKey = stringPreferencesKey("ai_visual_profiles")
    private val aiVisualBindingsKey = stringPreferencesKey("ai_visual_bindings")
    private val aiSolveInputModeKey = stringPreferencesKey("ai_solve_input_mode")
    private val aiCaptureInputModeKey = stringPreferencesKey("ai_capture_input_mode")
    private val aiUploadConsentKey = booleanPreferencesKey("ai_upload_consent")
    private val aiExcludeSourceImageByDefaultKey = booleanPreferencesKey("ai_exclude_source_image_by_default")
    private val dailyReviewLimitKey = intPreferencesKey("daily_review_limit")
    private val reviewSubjectsKey = stringPreferencesKey("review_subjects")
    private val reviewSubjectCatalogKey = stringPreferencesKey("review_subject_catalog")
    private val reviewPlanEnabledKey = booleanPreferencesKey("review_plan_enabled")
    private val randomReviewKey = booleanPreferencesKey("random_review")
    private val reviewCheckInsKey = stringPreferencesKey("review_check_ins")
    private val reviewProgressKey = stringPreferencesKey("review_progress")
    private val reviewMasteryKey = stringPreferencesKey("review_mastery")
    private val reviewPlanSnapshotsKey = stringPreferencesKey("review_plan_snapshots")
    private val legacyTagBackfillVersionKey = intPreferencesKey("legacy_tag_backfill_version")

    val themeMode: Flow<String> = context.tijiDataStore.data.map { preferences ->
        preferences[themeModeKey] ?: if (preferences[darkKey] == true) "dark" else "system"
    }
    val themePalette: Flow<String> = context.tijiDataStore.data.map { it[themePaletteKey] ?: "blue" }
    val aiEndpoint: Flow<String> = context.tijiDataStore.data.map { it[aiEndpointKey] ?: DEFAULT_ENDPOINT }
    val aiModel: Flow<String> = context.tijiDataStore.data.map { it[aiModelKey] ?: DEFAULT_MODEL }
    val aiProfiles: Flow<List<AiProfile>> = context.tijiDataStore.data.map { preferences ->
        decodeProfiles(preferences[aiProfilesKey], preferences[aiEndpointKey] ?: DEFAULT_ENDPOINT, preferences[aiModelKey] ?: DEFAULT_MODEL)
    }
    val activeAiProfileId: Flow<String> = context.tijiDataStore.data.map { it[activeAiProfileKey] ?: DEFAULT_PROFILE_ID }
    val aiVisualProfiles: Flow<List<AiVisualProfile>> = context.tijiDataStore.data.map {
        decodeVisualProfiles(it[aiVisualProfilesKey])
    }
    val aiVisualBindings: Flow<Map<String, String>> = context.tijiDataStore.data.map {
        decodeVisualBindings(it[aiVisualBindingsKey])
    }
    val aiSolveInputMode: Flow<String> = context.tijiDataStore.data.map { it[aiSolveInputModeKey] ?: DEFAULT_INPUT_MODE }
    val aiCaptureInputMode: Flow<String> = context.tijiDataStore.data.map { it[aiCaptureInputModeKey] ?: DEFAULT_INPUT_MODE }
    val aiUploadConsent: Flow<Boolean> = context.tijiDataStore.data.map { it[aiUploadConsentKey] ?: false }
    val aiExcludeSourceImageByDefault: Flow<Boolean> = context.tijiDataStore.data.map {
        it[aiExcludeSourceImageByDefaultKey] ?: true
    }
    val dailyReviewLimit: Flow<Int> = context.tijiDataStore.data.map { (it[dailyReviewLimitKey] ?: 20).coerceIn(1, 100) }
    val reviewSubjects: Flow<String> = context.tijiDataStore.data.map { it[reviewSubjectsKey] ?: "" }
    val reviewSubjectCatalog: Flow<List<String>> = context.tijiDataStore.data.map { decodeSubjectCatalog(it[reviewSubjectCatalogKey]) }
    val reviewPlanEnabled: Flow<Boolean> = context.tijiDataStore.data.map { it[reviewPlanEnabledKey] ?: false }
    val randomReview: Flow<Boolean> = context.tijiDataStore.data.map { it[randomReviewKey] ?: false }
    val reviewCheckIns: Flow<Set<String>> = context.tijiDataStore.data.map { decodeStringSet(it[reviewCheckInsKey]) }
    val reviewProgress: Flow<Map<String, Int>> = context.tijiDataStore.data.map { decodeProgress(it[reviewProgressKey]) }
    val reviewMastery: Flow<Map<String, Map<Long, String>>> = context.tijiDataStore.data.map {
        decodeReviewMastery(it[reviewMasteryKey])
    }
    val reviewPlanSnapshots: Flow<Map<String, List<Long>>> = context.tijiDataStore.data.map {
        decodeReviewPlanSnapshots(it[reviewPlanSnapshotsKey])
    }

    suspend fun isLegacyTagBackfillComplete(): Boolean = context.tijiDataStore.data
        .map { (it[legacyTagBackfillVersionKey] ?: 0) >= LEGACY_TAG_BACKFILL_VERSION }
        .first()

    suspend fun markLegacyTagBackfillComplete() {
        context.tijiDataStore.edit { it[legacyTagBackfillVersionKey] = LEGACY_TAG_BACKFILL_VERSION }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.tijiDataStore.edit { it[darkKey] = enabled }
    }

    suspend fun setThemeMode(value: String) {
        context.tijiDataStore.edit { it[themeModeKey] = value }
    }

    suspend fun setThemePalette(value: String) {
        context.tijiDataStore.edit { it[themePaletteKey] = value }
    }

    suspend fun setAiEndpoint(value: String) {
        context.tijiDataStore.edit { it[aiEndpointKey] = value.trim().trimEnd('/') }
    }

    suspend fun setAiModel(value: String) {
        context.tijiDataStore.edit { it[aiModelKey] = value.trim() }
    }

    suspend fun setAiProfiles(value: List<AiProfile>) {
        context.tijiDataStore.edit { preferences ->
            val uniqueProfiles = value.distinctBy { it.id }
            val default = uniqueProfiles.firstOrNull { it.id == DEFAULT_PROFILE_ID }
                ?: defaultProfile()
            val normalized = buildList {
                add(default)
                addAll(uniqueProfiles.filterNot { it.id == DEFAULT_PROFILE_ID })
            }
            preferences[aiProfilesKey] = encodeProfiles(normalized)
            val activeId = preferences[activeAiProfileKey] ?: DEFAULT_PROFILE_ID
            normalized.firstOrNull { it.id == activeId }?.let {
                preferences[aiEndpointKey] = it.endpoint.trim().trimEnd('/')
                preferences[aiModelKey] = it.model.trim()
            }
        }
    }

    suspend fun setActiveAiProfile(id: String, profiles: List<AiProfile>) {
        context.tijiDataStore.edit {
            it[activeAiProfileKey] = id
            profiles.firstOrNull { profile -> profile.id == id }?.let { active ->
                it[aiEndpointKey] = active.endpoint.trim().trimEnd('/')
                it[aiModelKey] = active.model.trim()
            }
        }
    }

    suspend fun setAiVisualProfiles(value: List<AiVisualProfile>) {
        context.tijiDataStore.edit { preferences ->
            preferences[aiVisualProfilesKey] = encodeVisualProfiles(value.distinctBy { it.id })
        }
    }

    suspend fun setAiVisualBinding(textProfileId: String, visualProfileId: String?) {
        if (textProfileId.isBlank()) return
        context.tijiDataStore.edit { preferences ->
            val bindings = decodeVisualBindings(preferences[aiVisualBindingsKey]).toMutableMap()
            if (visualProfileId.isNullOrBlank()) bindings.remove(textProfileId)
            else bindings[textProfileId] = visualProfileId
            preferences[aiVisualBindingsKey] = encodeVisualBindings(bindings)
        }
    }

    suspend fun removeAiVisualProfile(visualProfileId: String) {
        if (visualProfileId.isBlank()) return
        context.tijiDataStore.edit { preferences ->
            val profiles = decodeVisualProfiles(preferences[aiVisualProfilesKey])
                .filterNot { it.id == visualProfileId }
            val bindings = decodeVisualBindings(preferences[aiVisualBindingsKey])
                .filterValues { it != visualProfileId }
            preferences[aiVisualProfilesKey] = encodeVisualProfiles(profiles)
            preferences[aiVisualBindingsKey] = encodeVisualBindings(bindings)
        }
    }

    suspend fun removeAiVisualForTextProfile(textProfileId: String) {
        if (textProfileId.isBlank()) return
        context.tijiDataStore.edit { preferences ->
            val profiles = decodeVisualProfiles(preferences[aiVisualProfilesKey])
                .filterNot { it.textProfileId == textProfileId }
            val bindings = decodeVisualBindings(preferences[aiVisualBindingsKey]).toMutableMap()
            bindings.remove(textProfileId)
            preferences[aiVisualProfilesKey] = encodeVisualProfiles(profiles)
            preferences[aiVisualBindingsKey] = encodeVisualBindings(bindings)
        }
    }

    suspend fun setAiSolveInputMode(value: String) {
        context.tijiDataStore.edit { it[aiSolveInputModeKey] = value }
    }

    suspend fun setAiCaptureInputMode(value: String) {
        context.tijiDataStore.edit { it[aiCaptureInputModeKey] = value }
    }

    suspend fun setAiUploadConsent(value: Boolean) {
        context.tijiDataStore.edit { it[aiUploadConsentKey] = value }
    }

    suspend fun setAiExcludeSourceImageByDefault(value: Boolean) {
        context.tijiDataStore.edit { it[aiExcludeSourceImageByDefaultKey] = value }
    }

    suspend fun setDailyReviewLimit(value: Int) {
        context.tijiDataStore.edit { it[dailyReviewLimitKey] = value.coerceIn(1, 100) }
    }

    suspend fun setReviewSubjects(value: String) {
        context.tijiDataStore.edit { it[reviewSubjectsKey] = value.trim() }
    }
    suspend fun setReviewSubjectCatalog(value: List<String>) {
        val normalized = value.map(String::trim).filter(String::isNotBlank).distinct().take(30)
        context.tijiDataStore.edit { it[reviewSubjectCatalogKey] = JSONArray(normalized).toString() }
    }
    suspend fun setReviewPlanEnabled(value: Boolean) { context.tijiDataStore.edit { it[reviewPlanEnabledKey] = value } }
    suspend fun setRandomReview(value: Boolean) { context.tijiDataStore.edit { it[randomReviewKey] = value } }

    suspend fun recordReviewStatus(date: String, questionId: Long, status: String) {
        context.tijiDataStore.edit { preferences ->
            val records = decodeReviewMastery(preferences[reviewMasteryKey]).mapValues { it.value.toMutableMap() }.toMutableMap()
            val dateRecords = records[date] ?: mutableMapOf()
            dateRecords[questionId] = status
            records[date] = dateRecords
            preferences[reviewMasteryKey] = encodeReviewMastery(records)
        }
    }

    suspend fun ensureReviewPlanSnapshot(date: String, questionIds: List<Long>) {
        val normalized = questionIds.distinct().filter { it > 0L }
        if (normalized.isEmpty()) return
        context.tijiDataStore.edit { preferences ->
            val snapshots = decodeReviewPlanSnapshots(preferences[reviewPlanSnapshotsKey]).toMutableMap()
            if (snapshots[date].isNullOrEmpty()) {
                snapshots[date] = normalized
                preferences[reviewPlanSnapshotsKey] = encodeReviewPlanSnapshots(snapshots)
            }
        }
    }

    suspend fun removeFromReviewPlanSnapshot(date: String, questionId: Long) {
        if (questionId <= 0L) return
        context.tijiDataStore.edit { preferences ->
            val snapshots = decodeReviewPlanSnapshots(preferences[reviewPlanSnapshotsKey]).toMutableMap()
            val remaining = snapshots[date].orEmpty().filterNot { it == questionId }
            if (remaining.isEmpty()) snapshots.remove(date) else snapshots[date] = remaining
            preferences[reviewPlanSnapshotsKey] = encodeReviewPlanSnapshots(snapshots)
        }
    }

    suspend fun setReviewCheckIn(date: String, enabled: Boolean) {
        context.tijiDataStore.edit { preferences ->
            val checkIns = decodeStringSet(preferences[reviewCheckInsKey]).toMutableSet()
            if (enabled) checkIns += date else checkIns -= date
            preferences[reviewCheckInsKey] = JSONArray(checkIns.sorted()).toString()
        }
    }

    suspend fun recordReview(date: String = localDateKey()) {
        context.tijiDataStore.edit { preferences ->
            val progress = decodeProgress(preferences[reviewProgressKey]).toMutableMap()
            progress[date] = (progress[date] ?: 0) + 1
            preferences[reviewProgressKey] = JSONObject(progress).toString()
            val checkIns = decodeStringSet(preferences[reviewCheckInsKey]).toMutableSet()
            checkIns += date
            preferences[reviewCheckInsKey] = JSONArray(checkIns.sorted()).toString()
        }
    }

    suspend fun toggleReviewCheckIn(date: String) {
        context.tijiDataStore.edit { preferences ->
            val checkIns = decodeStringSet(preferences[reviewCheckInsKey]).toMutableSet()
            if (!checkIns.add(date)) checkIns.remove(date)
            preferences[reviewCheckInsKey] = JSONArray(checkIns.sorted()).toString()
        }
    }

    /** Clears review data while keeping appearance, AI profiles, and encrypted API keys intact. */
    suspend fun resetReviewData() {
        context.tijiDataStore.edit { preferences ->
            preferences.remove(reviewCheckInsKey)
            preferences.remove(reviewProgressKey)
            preferences.remove(reviewMasteryKey)
            preferences.remove(reviewPlanSnapshotsKey)
        }
    }

    /** Serializes only portable, non-secret settings. API keys and transient AI state live elsewhere. */
    suspend fun exportBackupJson(idToStableId: Map<Long, String>): JSONObject {
        val preferences = context.tijiDataStore.data.first()
        val mastery = JSONObject().apply {
            decodeReviewMastery(preferences[reviewMasteryKey]).forEach { (date, records) ->
                put(date, JSONObject().apply {
                    records.forEach { (questionId, status) ->
                        idToStableId[questionId]?.let { stableId -> put(stableId, status) }
                    }
                })
            }
        }
        val snapshots = JSONObject().apply {
            decodeReviewPlanSnapshots(preferences[reviewPlanSnapshotsKey]).forEach { (date, questionIds) ->
                put(date, JSONArray(questionIds.mapNotNull(idToStableId::get).distinct()))
            }
        }
        return JSONObject()
            .put("schemaVersion", 1)
            .put("themeMode", preferences[themeModeKey] ?: if (preferences[darkKey] == true) "dark" else "system")
            .put("themePalette", preferences[themePaletteKey] ?: "blue")
            .put("aiEndpoint", preferences[aiEndpointKey] ?: DEFAULT_ENDPOINT)
            .put("aiModel", preferences[aiModelKey] ?: DEFAULT_MODEL)
            .put("aiProfiles", JSONArray(preferences[aiProfilesKey] ?: encodeProfiles(listOf(defaultProfile()))))
            .put("activeAiProfile", preferences[activeAiProfileKey] ?: DEFAULT_PROFILE_ID)
            .put("aiUploadConsent", preferences[aiUploadConsentKey] ?: false)
            .put("aiExcludeSourceImageByDefault", preferences[aiExcludeSourceImageByDefaultKey] ?: true)
            .put("dailyReviewLimit", (preferences[dailyReviewLimitKey] ?: 20).coerceIn(1, 100))
            .put("reviewSubjects", preferences[reviewSubjectsKey] ?: "")
            .put("reviewSubjectCatalog", JSONArray(preferences[reviewSubjectCatalogKey] ?: "[]"))
            .put("reviewPlanEnabled", preferences[reviewPlanEnabledKey] ?: false)
            .put("randomReview", preferences[randomReviewKey] ?: false)
            .put("reviewCheckIns", JSONArray(preferences[reviewCheckInsKey] ?: "[]"))
            .put("reviewProgress", JSONObject(preferences[reviewProgressKey] ?: "{}"))
            .put("reviewMastery", mastery)
            .put("reviewPlanSnapshots", snapshots)
    }

    suspend fun importBackupJson(
        json: JSONObject?,
        stableIdToLocalId: Map<String, Long>,
        replace: Boolean
    ) {
        if (json == null) {
            if (replace) {
                context.tijiDataStore.edit { preferences ->
                    preferences[reviewCheckInsKey] = "[]"
                    preferences[reviewProgressKey] = "{}"
                    preferences[reviewMasteryKey] = "{}"
                    preferences[reviewPlanSnapshotsKey] = "{}"
                }
            }
            return
        }
        fun importedMastery(): Map<String, Map<Long, String>> {
            val root = json.optJSONObject("reviewMastery") ?: return emptyMap()
            return root.keys().asSequence().associateWith { date ->
                val records = root.optJSONObject(date) ?: JSONObject()
                records.keys().asSequence().mapNotNull { stableId ->
                    stableIdToLocalId[stableId]?.let { localId -> localId to records.optString(stableId) }
                }.toMap()
            }
        }
        fun importedSnapshots(): Map<String, List<Long>> {
            val root = json.optJSONObject("reviewPlanSnapshots") ?: return emptyMap()
            return root.keys().asSequence().associateWith { date ->
                val ids = root.optJSONArray(date) ?: JSONArray()
                (0 until ids.length()).mapNotNull { index ->
                    stableIdToLocalId[ids.optString(index)]
                }.distinct()
            }
        }

        context.tijiDataStore.edit { preferences ->
            preferences[themeModeKey] = json.optString("themeMode", "system")
            preferences[themePaletteKey] = json.optString("themePalette", "blue")
            preferences[aiEndpointKey] = json.optString("aiEndpoint", DEFAULT_ENDPOINT).trim().trimEnd('/')
            preferences[aiModelKey] = json.optString("aiModel", DEFAULT_MODEL).trim()
            preferences[aiProfilesKey] = (json.optJSONArray("aiProfiles") ?: JSONArray().put(
                JSONObject()
                    .put("id", DEFAULT_PROFILE_ID)
                    .put("name", "默认 AI")
                    .put("endpoint", json.optString("aiEndpoint", DEFAULT_ENDPOINT))
                    .put("model", json.optString("aiModel", DEFAULT_MODEL))
            )).toString()
            preferences[activeAiProfileKey] = json.optString("activeAiProfile", DEFAULT_PROFILE_ID)
            preferences[aiUploadConsentKey] = json.optBoolean("aiUploadConsent", false)
            preferences[aiExcludeSourceImageByDefaultKey] = json.optBoolean("aiExcludeSourceImageByDefault", true)
            preferences[dailyReviewLimitKey] = json.optInt("dailyReviewLimit", 20).coerceIn(1, 100)
            preferences[reviewSubjectsKey] = json.optString("reviewSubjects", "")
            preferences[reviewSubjectCatalogKey] = (json.optJSONArray("reviewSubjectCatalog") ?: JSONArray()).toString()
            preferences[reviewPlanEnabledKey] = json.optBoolean("reviewPlanEnabled", false)
            preferences[randomReviewKey] = json.optBoolean("randomReview", false)

            val importedCheckIns = decodeStringSet(json.optJSONArray("reviewCheckIns")?.toString())
            val currentCheckIns = if (replace) emptySet() else decodeStringSet(preferences[reviewCheckInsKey])
            preferences[reviewCheckInsKey] = JSONArray((currentCheckIns + importedCheckIns).sorted()).toString()

            val importedProgress = decodeProgress(json.optJSONObject("reviewProgress")?.toString())
            val progress = (if (replace) emptyMap() else decodeProgress(preferences[reviewProgressKey])).toMutableMap()
            importedProgress.forEach { (date, count) -> progress[date] = maxOf(progress[date] ?: 0, count) }
            preferences[reviewProgressKey] = JSONObject(progress).toString()

            val mastery = (if (replace) emptyMap() else decodeReviewMastery(preferences[reviewMasteryKey]))
                .mapValues { it.value.toMutableMap() }.toMutableMap()
            importedMastery().forEach { (date, records) ->
                val merged = mastery[date] ?: mutableMapOf()
                merged.putAll(records)
                mastery[date] = merged
            }
            preferences[reviewMasteryKey] = encodeReviewMastery(mastery)

            val snapshots = (if (replace) emptyMap() else decodeReviewPlanSnapshots(preferences[reviewPlanSnapshotsKey]))
                .mapValues { it.value.toMutableList() }.toMutableMap()
            importedSnapshots().forEach { (date, ids) ->
                snapshots[date] = ((snapshots[date] ?: emptyList()) + ids).distinct().toMutableList()
            }
            preferences[reviewPlanSnapshotsKey] = encodeReviewPlanSnapshots(snapshots)
        }
    }

    companion object {
        private const val LEGACY_TAG_BACKFILL_VERSION = 1
        const val DEFAULT_PROFILE_ID = "default"
        const val DEFAULT_INPUT_MODE = "VISION"
        const val DEFAULT_ENDPOINT = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-5.6-sol"
    }

    private fun defaultProfile() = AiProfile(DEFAULT_PROFILE_ID, "默认 AI", DEFAULT_ENDPOINT, DEFAULT_MODEL)

    private fun decodeProfiles(raw: String?, endpoint: String, model: String): List<AiProfile> {
        if (raw.isNullOrBlank()) return listOf(AiProfile(DEFAULT_PROFILE_ID, "默认 AI", endpoint, model))
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").trim().ifBlank { return@mapNotNull null }
                AiProfile(id, item.optString("name").ifBlank { "未命名配置" }, item.optString("endpoint"), item.optString("model"))
            }.ifEmpty { listOf(AiProfile(DEFAULT_PROFILE_ID, "默认 AI", endpoint, model)) }
        }.getOrElse { listOf(AiProfile(DEFAULT_PROFILE_ID, "默认 AI", endpoint, model)) }
    }

    private fun encodeProfiles(value: List<AiProfile>): String = JSONArray().apply {
        value.forEach { profile ->
            put(JSONObject().put("id", profile.id).put("name", profile.name).put("endpoint", profile.endpoint).put("model", profile.model))
        }
    }.toString()

    private fun decodeVisualProfiles(raw: String?): List<AiVisualProfile> = runCatching {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").trim().ifBlank { return@mapNotNull null }
            val textProfileId = item.optString("textProfileId").trim().ifBlank { return@mapNotNull null }
            AiVisualProfile(
                id = id,
                name = item.optString("name").ifBlank { "视觉辅助配置" },
                endpoint = item.optString("endpoint").trim().trimEnd('/'),
                model = item.optString("model").trim(),
                textProfileId = textProfileId,
                keyProfileId = item.optString("keyProfileId").trim().takeIf(String::isNotBlank)
            )
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    private fun encodeVisualProfiles(value: List<AiVisualProfile>): String = JSONArray().apply {
        value.forEach { profile ->
            put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("endpoint", profile.endpoint)
                    .put("model", profile.model)
                    .put("textProfileId", profile.textProfileId)
                    .put("keyProfileId", profile.keyProfileId ?: JSONObject.NULL)
            )
        }
    }.toString()

    private fun decodeVisualBindings(raw: String?): Map<String, String> = runCatching {
        if (raw.isNullOrBlank()) return emptyMap()
        val json = JSONObject(raw)
        json.keys().asSequence().mapNotNull { key ->
            json.optString(key).trim().takeIf(String::isNotBlank)?.let { key to it }
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun encodeVisualBindings(value: Map<String, String>): String = JSONObject().apply {
        value.filterKeys(String::isNotBlank).filterValues(String::isNotBlank).forEach { (textId, visualId) ->
            put(textId, visualId)
        }
    }.toString()

    private fun decodeSubjectCatalog(raw: String?): List<String> = runCatching {
        if (raw.isNullOrBlank()) emptyList() else {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it).trim().ifBlank { null } }.distinct()
        }
    }.getOrDefault(emptyList())

    private fun decodeStringSet(raw: String?): Set<String> = runCatching {
        if (raw.isNullOrBlank()) emptySet() else {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet()
        }
    }.getOrDefault(emptySet())

    private fun decodeProgress(raw: String?): Map<String, Int> = runCatching {
        if (raw.isNullOrBlank()) emptyMap() else {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { key -> json.optInt(key, 0).coerceAtLeast(0) }
        }
    }.getOrDefault(emptyMap())

    private fun decodeReviewMastery(raw: String?): Map<String, Map<Long, String>> = runCatching {
        if (raw.isNullOrBlank()) emptyMap() else {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { date ->
                val dateJson = json.optJSONObject(date) ?: JSONObject()
                dateJson.keys().asSequence().mapNotNull { id ->
                    id.toLongOrNull()?.let { questionId -> questionId to dateJson.optString(id) }
                }.toMap()
            }
        }
    }.getOrDefault(emptyMap())

    private fun encodeReviewMastery(value: Map<String, Map<Long, String>>): String = JSONObject().apply {
        value.forEach { (date, records) ->
            put(date, JSONObject().apply {
                records.forEach { (questionId, status) -> put(questionId.toString(), status) }
            })
        }
    }.toString()

    private fun decodeReviewPlanSnapshots(raw: String?): Map<String, List<Long>> = runCatching {
        if (raw.isNullOrBlank()) emptyMap() else {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { date ->
                val array = json.optJSONArray(date) ?: JSONArray()
                (0 until array.length()).mapNotNull { index -> array.optLong(index).takeIf { it > 0L } }
            }
        }
    }.getOrDefault(emptyMap())

    private fun encodeReviewPlanSnapshots(value: Map<String, List<Long>>): String = JSONObject().apply {
        value.forEach { (date, questionIds) ->
            put(date, JSONArray(questionIds.distinct().filter { it > 0L }))
        }
    }.toString()

    private fun localDateKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}
