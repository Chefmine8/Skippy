package com.skippy.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class UiState(
    val availableGroups: List<ApiGroup>,
    val settings: AppSettings,
    val sessions: List<Session>,
    val allRawSessions: List<Session>,
    val att: Map<String, Status>,
    val typeNames: Map<Int, String>,
    val stats: List<SubjectStats>,
    val alerts: List<SubjectStats>,
    val allSubjects: List<String>,
    val excludedSubjects: Set<String>,
    val examMappings: Map<String, String>,
    val availableExams: List<String>,
    val now: Long,
    val syncing: Boolean,
    val progress: Pair<Int, Int>?,
    val message: String?,
)

data class DetailsState(val loading: Boolean, val details: ReservationDetails?, val error: String?)

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = Repo.get(app)
    private var syncing = false
    private var progress: Pair<Int, Int>? = null
    private var message: String? = null

    private val _ui = MutableStateFlow(build())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _details = MutableStateFlow<DetailsState?>(null)
    val details: StateFlow<DetailsState?> = _details.asStateFlow()

    private fun build(): UiState {
        val s = repo.settings()
        val sessions = repo.sessions()
        val att = repo.attendance()
        val names = repo.typeNames()
        val now = System.currentTimeMillis()
        val examMappings = repo.examMappings()
        val availableExams = repo.availableExams()
        val stats = Stats.compute(sessions, att, repo.prefs(), examMappings, availableExams, now, s.requiredPct, names) { typeName(names, it) }
        return UiState(
            availableGroups = repo.cachedGroups(),
            settings = s,
            sessions = sessions,
            allRawSessions = repo.allRawSessions(),
            att = att,
            typeNames = names,
            stats = stats,
            alerts = Stats.alerts(stats, s.alertAt),
            allSubjects = repo.allSubjects(),
            excludedSubjects = repo.excludedSubjects(),
            examMappings = examMappings,
            availableExams = availableExams,
            now = now,
            syncing = syncing,
            progress = progress,
            message = message,
        )
    }

    fun reload() {
        _ui.value = build()
    }

    fun fetchGroupsIfNeeded() {
        if (repo.cachedGroups().isNotEmpty()) return
        viewModelScope.launch {
            val token = Auth.accessToken(app) ?: return@launch
            runCatching { ZeusApi.groups(token) }
                .onSuccess {
                    if (it.isNotEmpty()) {
                        repo.saveGroups(it)
                        reload()
                    }
                }
        }
    }

    fun showMessage(text: String) {
        message = text
        reload()
    }

    fun clearMessage() {
        message = null
        reload()
    }

    // ---- attendance / preferences ---------------------------------------------------------

    fun setStatus(uid: String, status: Status?) {
        repo.setStatus(uid, status)
        reload()
    }

    fun setPref(subject: String, pref: Int) {
        repo.setPref(subject, pref)
        reload()
    }

    fun setExamMapping(subject: String, examSubject: String) {
        repo.setExamMapping(subject, examSubject)
        reload()
    }

    fun setTypeName(id: Int, label: String) {
        repo.setTypeName(id, label)
        reload()
    }

    fun setSubjectExcluded(subject: String, excluded: Boolean) {
        repo.setSubjectExcluded(subject, excluded)
        reload()
    }

    fun saveSettings(s: AppSettings) {
        val old = repo.settings()
        repo.saveSettings(s)
        reload()
        val changed = s.groupIds != old.groupIds || s.rentree.trim() != old.rentree
        if (changed && old.authMode.isNotEmpty()) sync(full = true)
    }

    // ---- authentication -------------------------------------------------------------------

    fun saveManualToken(token: String) {
        viewModelScope.launch {
            Auth.saveManual(app, token)
            reload()
            sync(full = true)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            Auth.signOut(app)
            reload()
        }
    }

    // ---- sync -----------------------------------------------------------------------------

    /** full = whole history since the school-year start; otherwise a quick refresh. The very first sync is always full. */
    fun sync(full: Boolean = false) {
        if (syncing) return
        syncing = true
        progress = null
        reload()
        viewModelScope.launch {
            val doFull = full || repo.settings().lastFull == 0L
            val result = repo.sync(doFull) { done, total ->
                progress = done to total
                reload()
            }
            syncing = false
            progress = null
            message = result.fold(
                onSuccess = { app.resources.getQuantityString(R.plurals.sync_ok, it, it) },
                onFailure = { e ->
                    if (e is ZeusAuthException) app.getString(R.string.auth_expired)
                    else app.getString(R.string.sync_fail, e.message ?: "?")
                },
            )
            reload()
        }
    }

    // ---- reservation details (GET api/reservation/{id}/details) ----------------------------

    fun showDetails(uid: String) {
        _details.value = DetailsState(loading = true, details = null, error = null)
        viewModelScope.launch {
            val result = runCatching {
                val token = Auth.accessToken(app) ?: throw ZeusAuthException("No token")
                ZeusApi.details(token, uid.toLong())
            }
            _details.value = DetailsState(
                loading = false,
                details = result.getOrNull(),
                error = result.exceptionOrNull()?.let { e ->
                    if (e is ZeusAuthException) app.getString(R.string.auth_expired)
                    else app.getString(R.string.error_generic, e.message ?: "?")
                },
            )
        }
    }

    fun closeDetails() {
        _details.value = null
    }
}
