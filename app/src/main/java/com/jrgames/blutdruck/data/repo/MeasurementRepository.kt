package com.jrgames.blutdruck.data.repo

import com.jrgames.blutdruck.data.local.MeasurementDao
import com.jrgames.blutdruck.data.local.MeasurementSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MeasurementRepository(private val dao: MeasurementDao) {

    suspend fun save(session: MeasurementSession): Long = dao.insert(session)

    fun observeAll(): Flow<List<MeasurementSession>> = dao.observeAll()

    fun observeInRange(fromMs: Long, toMs: Long): Flow<List<MeasurementSession>> =
        dao.observeInRange(fromMs, toMs)

    fun observeLatest(): Flow<MeasurementSession?> = dao.observeLatest()

    /** Einzigartige, nicht-leere Hinweise (neueste zuerst) als Vorschlagsliste. */
    fun observeNoteSuggestions(): Flow<List<String>> =
        dao.observeRecentNotes().map { notes ->
            notes.map(String::trim)
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
        }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
