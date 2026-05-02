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

    /** Hintergrundfarbe für die Listenansicht (Compose Color). */
    fun cardColor(cat: BpCategory): Color = when (cat) {
        BpCategory.OPTIMAL     -> Color(0xFFE8F5E9)  // helles Grün
        BpCategory.NORMAL      -> Color(0xFFF1F8E9)  // sehr helles Grün
        BpCategory.HIGH_NORMAL -> Color(0xFFFFF8E1)  // helles Gelb
        BpCategory.GRADE_1     -> Color(0xFFFFEBEE)  // helles Rot
        BpCategory.GRADE_2     -> Color(0xFFFFCDD2)  // mittleres Rot
        BpCategory.GRADE_3     -> Color(0xFFEF9A9A)  // kräftiges Rot
    }

    fun textColor(cat: BpCategory): Color = when (cat) {
        BpCategory.OPTIMAL, BpCategory.NORMAL -> Color(0xFF1B5E20)
        BpCategory.HIGH_NORMAL                -> Color(0xFFF57F17)
        else                                  -> Color(0xFFB71C1C)
    }
}

