package com.lockcal.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lockcal.app.R
import com.lockcal.app.data.CalendarRepository
import com.lockcal.app.databinding.ActivityMainBinding
import com.lockcal.app.databinding.DialogAddCalendarBinding
import com.lockcal.app.model.CalendarFeed
import com.lockcal.app.service.CalendarSyncWorker
import com.lockcal.app.service.LockscreenNotificationManager
import com.lockcal.app.widget.LockCalendarWidgetProvider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: CalendarRepository
    private lateinit var notificationManager: LockscreenNotificationManager

    private lateinit var feedAdapter: FeedAdapter
    private lateinit var eventAdapter: EventAdapter

    private val colorPalettes = arrayOf("#3B82F6", "#10B981", "#8B5CF6", "#F97316", "#EC4899", "#06B6D4")

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val calGranted = perms[Manifest.permission.READ_CALENDAR] ?: false
        if (calGranted) {
            performSync()
        }
        notificationManager.updateLockscreenNotification()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CalendarRepository(this)
        notificationManager = LockscreenNotificationManager(this)

        checkPermissions()
        setupUI()
        loadData()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            performSync()
        }
    }

    override fun onResume() {
        super.onResume()
        checkNotificationStatus()
        loadData()
        notificationManager.updateLockscreenNotification()
    }

    private fun checkNotificationStatus() {
        val areNotifsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        binding.cardNotificationWarning.visibility = if (areNotifsEnabled) View.GONE else View.VISIBLE
    }

    private fun openSystemNotificationSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun showLockscreenHelpDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Lock Screen Setup Tips")
            .setMessage(
                "To ensure your agenda displays fully on your lock screen:\n\n" +
                "• Samsung One UI: Go to Phone Settings > Lock screen > Notifications and select 'Details' (Samsung defaults to 'Icons only').\n\n" +
                "• Xiaomi / MIUI / HyperOS: Go to Phone Settings > Apps > LockCal > Notifications and turn on 'Show on Lock screen'.\n\n" +
                "• Lock Screen Widget: You can also long-press your Lock Screen or Home Screen and add the LockCal widget!"
            )
            .setPositiveButton("Open Settings") { _, _ ->
                openSystemNotificationSettings()
            }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun checkPermissions() {
        val toRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            toRequest.add(Manifest.permission.READ_CALENDAR)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (toRequest.isNotEmpty()) {
            permissionsLauncher.launch(toRequest.toTypedArray())
        }
    }

    private fun setupUI() {
        // Setup Feeds RecyclerView
        feedAdapter = FeedAdapter(emptyList()) { feed ->
            confirmDeleteFeed(feed)
        }
        binding.rvFeeds.layoutManager = LinearLayoutManager(this)
        binding.rvFeeds.adapter = feedAdapter

        // Setup Events RecyclerView
        eventAdapter = EventAdapter(emptyList())
        binding.rvEvents.layoutManager = LinearLayoutManager(this)
        binding.rvEvents.adapter = eventAdapter

        // Switch settings
        binding.switchLockscreen.isChecked = repository.isLockscreenEnabled()
        binding.switchLockscreen.setOnCheckedChangeListener { _, isChecked ->
            repository.setLockscreenEnabled(isChecked)
            notificationManager.updateLockscreenNotification()
        }

        binding.switchHidePrivate.isChecked = repository.isHidePrivateEnabled()
        binding.switchHidePrivate.setOnCheckedChangeListener { _, isChecked ->
            repository.setHidePrivateEnabled(isChecked)
            notificationManager.updateLockscreenNotification()
        }

        // Notification buttons
        binding.btnEnableNotifications.setOnClickListener {
            openSystemNotificationSettings()
        }

        binding.btnSystemNotifSettings.setOnClickListener {
            openSystemNotificationSettings()
        }

        binding.btnLockscreenHelp.setOnClickListener {
            showLockscreenHelpDialog()
        }

        // Action Buttons
        binding.btnSyncNow.setOnClickListener {
            performSync()
        }

        binding.btnAddFeed.setOnClickListener {
            showAddFeedDialog()
        }

        binding.btnGlancePreview.setOnClickListener {
            startActivity(Intent(this, LockScreenGlanceActivity::class.java))
        }
    }

    private fun loadData() {
        val feeds = repository.getFeeds()
        feedAdapter.updateData(feeds)
        binding.tvEmptyFeeds.visibility = if (feeds.isEmpty()) View.VISIBLE else View.GONE
        binding.rvFeeds.visibility = if (feeds.isEmpty()) View.GONE else View.VISIBLE

        val cachedEvents = repository.getCachedEvents()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val upcoming = cachedEvents.filter { it.endMillis >= startOfToday }
        eventAdapter.updateData(upcoming)
        binding.tvEmptyEvents.visibility = if (upcoming.isEmpty()) View.VISIBLE else View.GONE
        binding.rvEvents.visibility = if (upcoming.isEmpty()) View.GONE else View.VISIBLE

        val lastSync = repository.getLastSyncTime()
        if (lastSync > 0) {
            val format = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            binding.tvSyncStatus.text = getString(R.string.status_synced, format.format(Date(lastSync)))
        } else {
            binding.tvSyncStatus.text = getString(R.string.status_no_feeds)
        }
    }

    private fun performSync() {
        binding.tvSyncStatus.text = getString(R.string.status_syncing)
        binding.btnSyncNow.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = repository.syncAllFeeds()
                binding.btnSyncNow.isEnabled = true

                if (result.isSuccess) {
                    val events = result.getOrNull() ?: emptyList()
                    val startOfToday = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val upcoming = events.filter { it.endMillis >= startOfToday }
                    eventAdapter.updateData(upcoming)
                    feedAdapter.updateData(repository.getFeeds())

                    binding.tvEmptyEvents.visibility = if (upcoming.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvEvents.visibility = if (upcoming.isEmpty()) View.GONE else View.VISIBLE

                    try {
                        notificationManager.updateLockscreenNotification()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    try {
                        LockCalendarWidgetProvider.updateAllWidgets(this@MainActivity)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val format = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    binding.tvSyncStatus.text = getString(R.string.status_synced, format.format(Date()))
                    Toast.makeText(this@MainActivity, R.string.toast_sync_success, Toast.LENGTH_SHORT).show()
                } else {
                    binding.tvSyncStatus.text = "Sync failed"
                    Toast.makeText(
                        this@MainActivity,
                        result.exceptionOrNull()?.message ?: getString(R.string.toast_sync_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                binding.btnSyncNow.isEnabled = true
                binding.tvSyncStatus.text = "Sync error"
                Toast.makeText(this@MainActivity, "Sync Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showAddFeedDialog() {
        val dialogBinding = DialogAddCalendarBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnDialogCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnDialogSave.setOnClickListener {
            val name = dialogBinding.etFeedName.text?.toString()?.trim().orEmpty()
            val rawUrl = dialogBinding.etFeedUrl.text?.toString()?.trim().orEmpty()

            if (name.isBlank()) {
                dialogBinding.etFeedName.error = "Please enter a name"
                return@setOnClickListener
            }

            var cleanUrl = rawUrl
            if (cleanUrl.isNotBlank() &&
                !cleanUrl.startsWith("http://", ignoreCase = true) &&
                !cleanUrl.startsWith("https://", ignoreCase = true) &&
                !cleanUrl.startsWith("webcal://", ignoreCase = true)) {
                cleanUrl = "https://$cleanUrl"
            }

            if (cleanUrl.isBlank()) {
                dialogBinding.etFeedUrl.error = getString(R.string.toast_invalid_url)
                return@setOnClickListener
            }

            val chosenColor = colorPalettes[repository.getFeeds().size % colorPalettes.size]
            val newFeed = CalendarFeed(
                name = name,
                url = cleanUrl,
                colorHex = chosenColor
            )

            repository.saveFeed(newFeed)
            dialog.dismiss()
            loadData()
            performSync()
        }

        dialog.show()
    }

    private fun confirmDeleteFeed(feed: CalendarFeed) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete)
            .setMessage("Remove calendar \"${feed.name}\"?")
            .setPositiveButton(R.string.dialog_delete) { _, _ ->
                repository.deleteFeed(feed.id)
                loadData()
                notificationManager.updateLockscreenNotification()
                LockCalendarWidgetProvider.updateAllWidgets(this)
                Toast.makeText(this, R.string.toast_feed_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
