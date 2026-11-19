package com.example.ilactanima

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * Birden fazla tespit edilen ilaç için yana kaydırılabilir bilgi kartları.
 * Her kart view_info_card.xml layout'unu kullanır.
 */
class InfoCardAdapter : RecyclerView.Adapter<InfoCardAdapter.VH>() {

    data class Item(val detection: Detection, val info: MedicineInfo?)

    private var items: List<Item> = emptyList()

    fun submit(newItems: List<Item>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(old: Int, new: Int) =
                items[old].detection.label == newItems[new].detection.label &&
                        items[old].detection.x1 == newItems[new].detection.x1

            override fun areContentsTheSame(old: Int, new: Int) =
                items[old] == newItems[new]
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }

    /** Mevcut kartların etiket setini döner (değişim algılamak için). */
    fun currentLabels(): List<String> = items.map { it.detection.label }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_info_card, parent, false)
        // Her kart RecyclerView genişliği kadar (snap için)
        view.layoutParams = ViewGroup.LayoutParams(
            parent.measuredWidth.takeIf { it > 0 } ?: parent.width,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        InfoCardBinder.bind(holder.itemView, item.detection, item.info)
    }

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view)
}