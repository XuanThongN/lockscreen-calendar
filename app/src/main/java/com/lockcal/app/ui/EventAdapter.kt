package com.lockcal.app.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lockcal.app.databinding.ItemCalendarEventBinding
import com.lockcal.app.model.CalendarEvent

class EventAdapter(
    private var events: List<CalendarEvent>
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    fun updateData(newEvents: List<CalendarEvent>) {
        events = newEvents
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemCalendarEventBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(events[position])
    }

    override fun getItemCount(): Int = events.size

    inner class EventViewHolder(private val binding: ItemCalendarEventBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(event: CalendarEvent) {
            binding.tvEventDate.text = event.formattedDate()
            binding.tvEventTime.text = event.formattedTime()
            binding.tvEventTitle.text = event.summary

            val details = StringBuilder()
            if (event.location.isNotBlank()) {
                details.append(event.location).append(" • ")
            }
            if (event.feedName.isNotBlank()) {
                details.append(event.feedName)
            }
            binding.tvEventDetails.text = details.toString()

            try {
                binding.viewEventColorTag.setBackgroundColor(Color.parseColor(event.colorHex))
            } catch (e: Exception) {
                // fallback
            }
        }
    }
}
