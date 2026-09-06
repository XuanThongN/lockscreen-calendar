package com.lockcal.app.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lockcal.app.databinding.ItemCalendarFeedBinding
import com.lockcal.app.model.CalendarFeed

class FeedAdapter(
    private var feeds: List<CalendarFeed>,
    private val onDeleteClick: (CalendarFeed) -> Unit
) : RecyclerView.Adapter<FeedAdapter.FeedViewHolder>() {

    fun updateData(newFeeds: List<CalendarFeed>) {
        feeds = newFeeds
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val binding = ItemCalendarFeedBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FeedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        holder.bind(feeds[position])
    }

    override fun getItemCount(): Int = feeds.size

    inner class FeedViewHolder(private val binding: ItemCalendarFeedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(feed: CalendarFeed) {
            binding.tvFeedTitle.text = feed.name
            binding.tvFeedUrl.text = feed.url

            try {
                binding.viewColorBar.setBackgroundColor(Color.parseColor(feed.colorHex))
            } catch (e: Exception) {
                // Fallback default color
            }

            binding.btnDeleteFeed.setOnClickListener {
                onDeleteClick(feed)
            }
        }
    }
}
