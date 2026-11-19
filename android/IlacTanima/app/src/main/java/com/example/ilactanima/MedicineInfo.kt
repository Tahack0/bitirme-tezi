package com.example.ilactanima

/**
 * SQLite'taki tek bir ilaç kaydı.
 */
data class MedicineInfo(
    val modelEtiketi: String,
    val ilacAdi: String,
    val neIseYarar: String?,
    val iyiGeldigi1: String?,
    val iyiGeldigi2: String?,
    val iyiGeldigi3: String?,
)
