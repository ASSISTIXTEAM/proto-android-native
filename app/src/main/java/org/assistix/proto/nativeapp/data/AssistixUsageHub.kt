package org.assistix.proto.nativeapp.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared PROTO AI token budget (5 h window), updated by every Assistix API call. */
object AssistixUsageHub {
    private val _budget = MutableStateFlow<AssistixRateLimit?>(null)
    val budget: StateFlow<AssistixRateLimit?> = _budget.asStateFlow()

    fun apply(limit: AssistixRateLimit?) {
        if (limit != null && limit.limit > 0) {
            _budget.value = limit
        }
    }

    fun applyFromReply(reply: AssistixReply) {
        apply(reply.rateLimit)
    }

    fun clear() {
        _budget.value = null
    }
}
