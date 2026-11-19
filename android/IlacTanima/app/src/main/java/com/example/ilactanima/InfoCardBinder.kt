package com.example.ilactanima

import android.view.View
import android.widget.TextView

/**
 * view_info_card.xml içindeki TextView'ları tek noktadan dolduran yardımcı.
 */
object InfoCardBinder {

    fun bind(
        cardRoot: View,
        detection: Detection?,
        info: MedicineInfo?
    ) {
        val tvName = cardRoot.findViewById<TextView>(R.id.tvMedicineName)
        val tvConf = cardRoot.findViewById<TextView>(R.id.tvConfidence)

        val tvWhatForLabel = cardRoot.findViewById<TextView>(R.id.tvWhatForLabel)
        val tvWhatFor = cardRoot.findViewById<TextView>(R.id.tvWhatFor)

        val tvUsedForLabel = cardRoot.findViewById<TextView>(R.id.tvUsedForLabel)
        val tvUsedFor1 = cardRoot.findViewById<TextView>(R.id.tvUsedFor1)
        val tvUsedFor2 = cardRoot.findViewById<TextView>(R.id.tvUsedFor2)
        val tvUsedFor3 = cardRoot.findViewById<TextView>(R.id.tvUsedFor3)

        if (detection == null) {
            tvName.text = cardRoot.context.getString(R.string.hint_camera_no_detection)
            tvConf.visibility = View.GONE
            tvWhatForLabel.visibility = View.GONE
            tvWhatFor.visibility = View.GONE
            tvUsedForLabel.visibility = View.GONE
            tvUsedFor1.visibility = View.GONE
            tvUsedFor2.visibility = View.GONE
            tvUsedFor3.visibility = View.GONE
            return
        }

        tvConf.visibility = View.VISIBLE
        tvConf.text = cardRoot.context.getString(R.string.confidence_pct, (detection.score * 100).toInt())

        if (info == null) {
            tvName.text = detection.label
            tvWhatForLabel.visibility = View.VISIBLE
            tvWhatFor.visibility = View.VISIBLE
            tvWhatFor.text = cardRoot.context.getString(R.string.label_no_db_info)
            tvUsedForLabel.visibility = View.GONE
            tvUsedFor1.visibility = View.GONE
            tvUsedFor2.visibility = View.GONE
            tvUsedFor3.visibility = View.GONE
            return
        }

        tvName.text = info.ilacAdi

        tvWhatForLabel.visibility = if (!info.neIseYarar.isNullOrBlank()) View.VISIBLE else View.GONE
        tvWhatFor.visibility = tvWhatForLabel.visibility
        tvWhatFor.text = info.neIseYarar.orEmpty()

        val uses = listOf(info.iyiGeldigi1, info.iyiGeldigi2, info.iyiGeldigi3)
            .map { it?.trim().orEmpty() }
        val hasAnyUse = uses.any { it.isNotBlank() }
        tvUsedForLabel.visibility = if (hasAnyUse) View.VISIBLE else View.GONE

        fun setBullet(tv: TextView, text: String) {
            if (text.isBlank()) {
                tv.visibility = View.GONE
            } else {
                tv.visibility = View.VISIBLE
                tv.text = tv.context.getString(R.string.bullet_item, text)
            }
        }
        setBullet(tvUsedFor1, uses[0])
        setBullet(tvUsedFor2, uses[1])
        setBullet(tvUsedFor3, uses[2])
    }
}
