package org.assistix.proto.nativeapp.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Global upload/download/cells progress visible across the app. */
object ProtoTransferProgressHub {
    data class Job(
        val id: String,
        val label: String,
        /** 0..1, or -1 for indeterminate */
        val progress: Float,
    )

    private val jobs = ConcurrentHashMap<String, Job>()
    private val _active = MutableStateFlow<List<Job>>(emptyList())
    val active: StateFlow<List<Job>> = _active.asStateFlow()

    fun begin(id: String, label: String) {
        jobs[id] = Job(id, label, -1f)
        publish()
    }

    fun update(id: String, progress: Float, label: String? = null) {
        val prev = jobs[id] ?: return
        jobs[id] = prev.copy(progress = progress.coerceIn(0f, 1f), label = label ?: prev.label)
        publish()
    }

    fun end(id: String) {
        jobs.remove(id)
        publish()
    }

    private fun publish() {
        _active.update { jobs.values.sortedBy { it.id } }
    }
}
