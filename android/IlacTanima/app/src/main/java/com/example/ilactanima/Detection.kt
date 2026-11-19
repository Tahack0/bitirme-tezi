package com.example.ilactanima

/**
 * Tek bir YOLO tespit kaydı.
 * Koordinatlar kaynak görüntü uzayında 0-1 normalize edilmiş hâldedir.
 */
data class Detection(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val score: Float,
    val classId: Int,
    val label: String
)
