package com.example.uberfrontend.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.uberfrontend.data.model.DriverTripUi
import com.example.uberfrontend.databinding.ItemTripBinding
import java.util.Locale

class EarningsAdapter :
    ListAdapter<DriverTripUi, EarningsAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<DriverTripUi>() {
        override fun areItemsTheSame(oldItem: DriverTripUi, newItem: DriverTripUi) =
            oldItem.tripId == newItem.tripId

        override fun areContentsTheSame(oldItem: DriverTripUi, newItem: DriverTripUi) =
            oldItem == newItem
    }

    inner class VH(val binding: ItemTripBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemTripBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvTripTitle.text = item.title
        holder.binding.tvTripSubtitle.text = item.subtitle
        holder.binding.tvTripAmount.text = String.format(Locale.US, "$%.2f", item.amount)
    }
}
