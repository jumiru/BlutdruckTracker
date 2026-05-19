package com.jrgames.blutdruck.domain

import androidx.compose.ui.graphics.Color

/**
 * Klassifizierung nach ESH/DGK-Leitlinien 2023.
 * Grundlage: systolischer UND/ODER diastolischer Wert.
 */
enum class BpCategory(
    val label: String,
    val shortLabel: String,
) {
    OPTIMAL    ("Optimal",           "Optimal"),
    NORMAL     ("Normal",            "Normal"),
    HIGH_NORMAL("Hoch-normal",       "Hoch-n."),
    GRADE_1    ("Hypertonie Grad 1", "Grad 1"),
    GRADE_2    ("Hypertonie Grad 2", "Grad 2"),
    GRADE_3    ("Hypertonie Grad 3", "Grad 3"),
}

enum class PulsedruckCategory(
    val label:     String,
    val cardColor: Color,
    val textColor: Color,
) {
    VERY_LOW ("Sehr niedrig", Color(0xFFE3F2FD), Color(0xFF0D47A1)),
    LOW      ("Niedrig",      Color(0xFFE8EAF6), Color(0xFF283593)),
    NORMAL   ("Normal",       Color(0xFFE8F5E9), Color(0xFF1B5E20)),
    ELEVATED ("Erhöht",       Color(0xFFFFF8E1), Color(0xFFF57F17)),
    HIGH     ("Stark erhöht", Color(0xFFFFEBEE), Color(0xFFB71C1C)),
}

object BpClassifier {

    fun classify(sys: Int, dia: Int): BpCategory = when {
        sys >= 180 || dia >= 110 -> BpCategory.GRADE_3
        sys >= 160 || dia >= 100 -> BpCategory.GRADE_2
        sys >= 140 || dia >= 90  -> BpCategory.GRADE_1
        sys >= 130 || dia >= 85  -> BpCategory.HIGH_NORMAL
        sys >= 120 || dia >= 80  -> BpCategory.NORMAL
        else                     -> BpCategory.OPTIMAL
    }

    fun classify(sys: Float, dia: Float): BpCategory = classify(sys.toInt(), dia.toInt())

    fun classifySys(sys: Float): BpCategory = when {
        sys >= 180 -> BpCategory.GRADE_3
        sys >= 160 -> BpCategory.GRADE_2
        sys >= 140 -> BpCategory.GRADE_1
        sys >= 130 -> BpCategory.HIGH_NORMAL
        sys >= 120 -> BpCategory.NORMAL
        else       -> BpCategory.OPTIMAL
    }

    fun classifyDia(dia: Float): BpCategory = when {
        dia >= 110 -> BpCategory.GRADE_3
        dia >= 100 -> BpCategory.GRADE_2
        dia >= 90  -> BpCategory.GRADE_1
        dia >= 85  -> BpCategory.HIGH_NORMAL
        dia >= 80  -> BpCategory.NORMAL
        else       -> BpCategory.OPTIMAL
    }

    fun classifyPulsedruck(pp: Float): PulsedruckCategory = when {
        pp < 30 -> PulsedruckCategory.VERY_LOW
        pp < 40 -> PulsedruckCategory.LOW
        pp < 60 -> PulsedruckCategory.NORMAL
        pp < 80 -> PulsedruckCategory.ELEVATED
        else    -> PulsedruckCategory.HIGH
    }

    /** Hintergrundfarbe für die Listenansicht (Compose Color). */
    fun cardColor(cat: BpCategory): Color = when (cat) {
        BpCategory.OPTIMAL     -> Color(0xFFE8F5E9)
        BpCategory.NORMAL      -> Color(0xFFF1F8E9)
        BpCategory.HIGH_NORMAL -> Color(0xFFFFF8E1)
        BpCategory.GRADE_1     -> Color(0xFFFFEBEE)
        BpCategory.GRADE_2     -> Color(0xFFFFCDD2)
        BpCategory.GRADE_3     -> Color(0xFFEF9A9A)
    }

    fun textColor(cat: BpCategory): Color = when (cat) {
        BpCategory.OPTIMAL, BpCategory.NORMAL -> Color(0xFF1B5E20)
        BpCategory.HIGH_NORMAL                -> Color(0xFFF57F17)
        else                                  -> Color(0xFFB71C1C)
    }
}

