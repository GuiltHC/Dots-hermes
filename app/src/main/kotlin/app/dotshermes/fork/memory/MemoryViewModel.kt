package app.dotshermes.fork.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dotshermes.data.AgentRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Раздел памяти Hermes: MEMORY.md (заметки агента) или USER.md (что агент знает о владельце). */
enum class MemoryTarget(val wire: String) { Memory("memory"), User("user") }

data class MemoryState(
    val memory: List<String> = emptyList(),
    val user: List<String> = emptyList(),
    val limits: Map<MemoryTarget, Int> = emptyMap(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    /** Плагин на сервере не знает memory_get: нужно обновить расширение форка. */
    val unsupported: Boolean = false,
    /** Не подключено к шлюзу. */
    val offline: Boolean = false,
    /** Ошибка последней правки (лимит, проверка содержимого…) — текст Hermes. */
    val error: String? = null,
) {
    fun entries(target: MemoryTarget) = if (target == MemoryTarget.User) user else memory

    /** Символов занято так же, как считает Hermes: записи через разделитель «\n§\n». */
    fun used(target: MemoryTarget) = usedChars(entries(target))

    companion object {
        const val DELIMITER = "\n§\n"
        fun usedChars(entries: List<String>) = entries.joinToString(DELIMITER).length
    }
}

/** Экран «Память»: читает и правит память агента через трамы memory_get / memory_edit (fork_memory.py). */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModel(private val agents: AgentRegistry) : ViewModel() {
    private val _state = MutableStateFlow(MemoryState())
    val state: StateFlow<MemoryState> = _state.asStateFlow()
    private var timeout: Job? = null

    init {
        viewModelScope.launch {
            agents.activeRepo.flatMapLatest { it.forkFrames }.collect(::onFrame)
        }
    }

    fun load() = request(JSONObject().put("type", "memory_get"), saving = false)

    fun add(target: MemoryTarget, content: String) =
        edit(target, "add", old = "", content = content)

    fun replace(target: MemoryTarget, old: String, content: String) =
        if (content.isBlank()) remove(target, old) else edit(target, "replace", old, content)

    fun remove(target: MemoryTarget, old: String) = edit(target, "remove", old, content = "")

    fun clearError() = _state.update { it.copy(error = null) }

    private fun edit(target: MemoryTarget, action: String, old: String, content: String) = request(
        JSONObject().put("type", "memory_edit").put("target", target.wire).put("action", action)
            .put("old", old).put("content", content.trim()),
        saving = true,
    )

    private fun request(frame: JSONObject, saving: Boolean) {
        frame.put("ref", UUID.randomUUID().toString())
        if (!agents.activeRepo.value.sendForkFrame(frame.toString())) {
            _state.update { it.copy(offline = true, loading = false, saving = false) }
            return
        }
        _state.update { it.copy(offline = false, loading = !saving, saving = saving, error = null) }
        timeout?.cancel()
        timeout = viewModelScope.launch {
            delay(10_000)
            _state.update { if (it.loading || it.saving) it.copy(loading = false, saving = false, unsupported = !it.loaded) else it }
        }
    }

    private fun onFrame(frame: JSONObject) {
        when (frame.optString("type")) {
            "memory" -> {
                timeout?.cancel()
                val limits = frame.optJSONObject("limits")
                _state.update {
                    it.copy(
                        memory = frame.optJSONArray("memory").strings(),
                        user = frame.optJSONArray("user").strings(),
                        limits = buildMap {
                            limits?.optInt("memory")?.takeIf { n -> n > 0 }?.let { n -> put(MemoryTarget.Memory, n) }
                            limits?.optInt("user")?.takeIf { n -> n > 0 }?.let { n -> put(MemoryTarget.User, n) }
                        },
                        loaded = frame.optBoolean("ok") || it.loaded,
                        loading = false, saving = false, unsupported = false, offline = false,
                        error = if (frame.optBoolean("ok")) null else frame.optString("error").ifBlank { null },
                    )
                }
            }
            // Старый плагин без расширения форка отвечает unknown_type на memory_get.
            "error" -> if (frame.optString("code") == "unknown_type" && (_state.value.loading || _state.value.saving)) {
                timeout?.cancel()
                _state.update { it.copy(loading = false, saving = false, unsupported = true) }
            }
        }
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else List(length()) { i -> optString(i) }.filter { it.isNotBlank() }
}
