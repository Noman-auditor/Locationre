package com.nora.tunnel.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-memory log shared by UI and (later) the VPN service. Cleared when the process dies. */
object AppLog {
    data class Entry(val time: Long, val type: String, val message: String)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    /** type: CONNECT, DISCONNECT, ERROR or INFO */
    fun add(type: String, message: String) {
        _entries.update { (listOf(Entry(System.currentTimeMillis(), type, message)) + it).take(300) }
    }

    fun clear() { _entries.value = emptyList() }

    fun export(): String {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return _entries.value.joinToString("\n") { "${f.format(Date(it.time))} [${it.type}] ${it.message}" }
    }
}
