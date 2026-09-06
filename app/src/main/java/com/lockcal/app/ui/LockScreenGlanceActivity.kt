package com.lockcal.app.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.lockcal.app.data.CalendarRepository
import com.lockcal.app.databinding.ActivityLockScreenGlanceBinding

class LockScreenGlanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenGlanceBinding
    private lateinit var repository: CalendarRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow display over screen lock
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        binding = ActivityLockScreenGlanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CalendarRepository(this)
        setupEventsList()

        binding.btnGlanceClose.setOnClickListener {
            finish()
        }
    }

    private fun setupEventsList() {
        val now = System.currentTimeMillis()
        val upcoming = repository.getCachedEvents().filter { it.endMillis >= now }

        if (upcoming.isEmpty()) {
            binding.tvGlanceEmpty.visibility = View.VISIBLE
            binding.rvGlanceEvents.visibility = View.GONE
        } else {
            binding.tvGlanceEmpty.visibility = View.GONE
            binding.rvGlanceEvents.visibility = View.VISIBLE
            binding.rvGlanceEvents.layoutManager = LinearLayoutManager(this)
            binding.rvGlanceEvents.adapter = EventAdapter(upcoming)
        }
    }
}
