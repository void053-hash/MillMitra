package com.millmitra.app.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.millmitra.app.R
import com.millmitra.app.data.local.EntryType
import com.millmitra.app.data.local.MillEntry
import com.millmitra.app.databinding.ItemHistoryBinding

class HistoryAdapter(
    private val onCopyClick: (String) -> Unit,
    private val onWhatsAppClick: (String) -> Unit,
    private val onDeleteClick: (Long) -> Unit
) : ListAdapter<MillEntry, HistoryAdapter.HistoryViewHolder>(DIFF_CALLBACK) {

    inner class HistoryViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: MillEntry) {
            when (entry.entryType) {
                EntryType.TRANSLATE_DECODE -> {
                    binding.tvTypeBadge.text = "DECODE (HINGLISH)"
                    binding.tvTypeBadge.setBackgroundColor(
                        ContextCompat.getColor(binding.root.context, R.color.primary)
                    )
                    binding.tvTypeBadge.setTextColor(
                        ContextCompat.getColor(binding.root.context, android.R.color.white)
                    )
                }
                EntryType.DICTATE_REPORT -> {
                    binding.tvTypeBadge.text = "SHIFT REPORT"
                    binding.tvTypeBadge.setBackgroundColor(
                        ContextCompat.getColor(binding.root.context, R.color.mill_green)
                    )
                    binding.tvTypeBadge.setTextColor(
                        ContextCompat.getColor(binding.root.context, android.R.color.white)
                    )
                }
            }

            // Relative time formatting, e.g., "5 mins ago"
            val relativeTime = DateUtils.getRelativeTimeSpanString(
                entry.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            binding.tvTimestamp.text = relativeTime

            binding.tvInput.text = entry.inputText
            binding.tvOutput.text = entry.outputText

            binding.btnItemCopy.setOnClickListener {
                onCopyClick(entry.outputText)
            }

            binding.btnItemWhatsApp.setOnClickListener {
                onWhatsAppClick(entry.outputText)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(entry.id)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MillEntry>() {
            override fun areItemsTheSame(oldItem: MillEntry, newItem: MillEntry): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: MillEntry, newItem: MillEntry): Boolean {
                return oldItem == newItem
            }
        }
    }
}
