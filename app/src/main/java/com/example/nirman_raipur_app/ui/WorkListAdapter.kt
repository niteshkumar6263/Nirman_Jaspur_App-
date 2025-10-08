package com.example.nirman_raipur_app.ui

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.nirman_raipur_app.R

class WorkListAdapter(
    private val onItemClick: (WorkItem) -> Unit
) : ListAdapter<WorkItem, WorkListAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WorkItem>() {
            override fun areItemsTheSame(oldItem: WorkItem, newItem: WorkItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: WorkItem, newItem: WorkItem): Boolean =
                oldItem == newItem
        }
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumb: ImageView = itemView.findViewById(R.id.iv_thumb)
        private val tvType: TextView = itemView.findViewById(R.id.tv_type)
        private val tvYear: TextView = itemView.findViewById(R.id.tv_year)
        private val tvLocation: TextView = itemView.findViewById(R.id.tv_location)
        private val tvLatLong: TextView = itemView.findViewById(R.id.tv_latlong)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
        private val tvAmount: TextView = itemView.findViewById(R.id.tv_amount)
        private val tvLast: TextView = itemView.findViewById(R.id.tv_last_modified)
        private val btnView: View = itemView.findViewById(R.id.btn_view)

        fun bind(item: WorkItem) {
            tvType.text = item.type
            tvYear.text = item.year
            tvLocation.text = item.location
            tvStatus.text = item.status
            tvAmount.text = item.amountFormatted
            tvLast.text = item.lastModified

            // Load image (photo or camera placeholder)
            ivThumb.load(item.imageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_camera)
                error(R.drawable.ic_camera)
            }

            if (item.latitude != null && item.longitude != null) {
                tvLatLong.visibility = View.VISIBLE
                tvLatLong.text = "Lat: %.6f, Lng: %.6f".format(item.latitude, item.longitude)
            } else {
                tvLatLong.visibility = View.GONE
            }

            // Click on card → WorkDetailActivity
            itemView.setOnClickListener {
                val ctx = itemView.context
                val intent = Intent(ctx, WorkDetailActivity::class.java)
                intent.putExtra(WorkDetailActivity.EXTRA_WORK_ID, item.id)
                ctx.startActivity(intent)
            }

            // Eye icon → same as clicking card
            btnView.setOnClickListener { itemView.performClick() }

            // Camera icon → open camera to capture & upload
            ivThumb.setOnClickListener {
                val ctx = itemView.context
                if (ctx is WorkProgressActivity) {
                    ctx.openCameraAndUpload(item.id)
                } else {
                    Toast.makeText(ctx, "Camera unavailable in this context", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_work_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}
