package com.jrgames.blutdruck.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

private data class Reading(val sys: Int, val dia: Int, val pulse: Int)

/**
 * Eine Messsitzung: 1–3 Messungen am gleichen Arm.
 * Auswertung: 1 Messung → M1; 2 Messungen → ∅ M1+M2; 3 Messungen → ∅ M2+M3 (DGK-Empfehlung).
 */
@Entity(tableName = "measurement_sessions")
data class MeasurementSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long = System.currentTimeMillis(),
    /** "LINKS" oder "RECHTS" */
    val arm: String,

    // Messung 1 (Pflicht)
    val sys1: Int,
    val dia1: Int,
    val pulse1: Int,

    // Messung 2 (optional)
    val sys2: Int? = null,
    val dia2: Int? = null,
    val pulse2: Int? = null,

    // Messung 3 (optional)
    val sys3: Int? = null,
    val dia3: Int? = null,
    val pulse3: Int? = null,

    // Freitext-Hinweis
    val note: String? = null,
) {
    val measurementCount: Int
        get() = availableReadings.size

    /** Kurzbeschriftung, welche Messungen ausgewertet wurden. */
    val evaluationLabel: String
        get() = when (measurementCount) {
            1    -> "M1"
            2    -> "∅ M1+M2"
            else -> "∅ M2+M3"
        }

    val avgSys: Float
        get() = evaluatedReadings.map { it.sys }.average().toFloat()

    val avgDia: Float
        get() = evaluatedReadings.map { it.dia }.average().toFloat()

    val avgPulse: Float
        get() = evaluatedReadings.map { it.pulse }.average().toFloat()

    private val availableReadings: List<Reading>
        get() = buildList {
            add(Reading(sys1, dia1, pulse1))
            val s2 = sys2; val d2 = dia2; val p2 = pulse2
            if (s2 != null && d2 != null && p2 != null) add(Reading(s2, d2, p2))
            val s3 = sys3; val d3 = dia3; val p3 = pulse3
            if (s3 != null && d3 != null && p3 != null) add(Reading(s3, d3, p3))
        }

    private val evaluatedReadings: List<Reading>
        get() = when (availableReadings.size) {
            1    -> availableReadings.take(1)
            2    -> availableReadings.take(2)
            else -> availableReadings.takeLast(2)
        }
}
