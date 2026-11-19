package com.example.ilactanima

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream

/**
 * İlaç veritabanı erişimi.
 *
 * assets/database/ilaclar.db dosyasını uygulamanın özel klasörüne kopyalar
 * (SQLiteDatabase doğrudan assets'tan okuyamaz), sonra sorgular.
 *
 * COLLATE NOCASE → büyük/küçük harf farkını umursamadan eşleşme yapar.
 * Bu sayede label "Bilaxten_20mg" veya "bilaxten_20mg" olursa fark etmez.
 */
class MedicineRepository(context: Context) {

    companion object {
        private const val DB_NAME = "ilaclar.db"
        private const val ASSET_PATH = "database/ilaclar.db"
    }

    private val db: SQLiteDatabase

    init {
        val outFile = File(context.filesDir, DB_NAME)
        if (!outFile.exists()) {
            copyDatabaseFromAssets(context, outFile)
        }
        db = SQLiteDatabase.openDatabase(
            outFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
    }

    private fun copyDatabaseFromAssets(context: Context, dest: File) {
        context.assets.open(ASSET_PATH).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    fun findByModelLabel(modelLabel: String): MedicineInfo? {
        db.rawQuery(
            "SELECT model_etiketi, ilac_adi, ne_ise_yarar, " +
                "iyi_geldigi_1, iyi_geldigi_2, iyi_geldigi_3 " +
                "FROM ilaclar WHERE model_etiketi = ? COLLATE NOCASE LIMIT 1",
            arrayOf(modelLabel)
        ).use { c ->
            if (!c.moveToFirst()) return null
            return MedicineInfo(
                modelEtiketi = c.getString(0),
                ilacAdi = c.getString(1),
                neIseYarar = c.getStringOrNull(2),
                iyiGeldigi1 = c.getStringOrNull(3),
                iyiGeldigi2 = c.getStringOrNull(4),
                iyiGeldigi3 = c.getStringOrNull(5)
            )
        }
    }

    private fun Cursor.getStringOrNull(idx: Int): String? =
        if (isNull(idx)) null else getString(idx)

    fun close() {
        db.close()
    }
}
