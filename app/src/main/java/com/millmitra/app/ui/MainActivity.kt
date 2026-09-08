package com.millmitra.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.millmitra.app.MillMitraApp
import com.millmitra.app.R
import com.millmitra.app.databinding.ActivityMainBinding
import com.millmitra.app.service.MillMitraOverlayService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val app by lazy { MillMitraApp.instance }
    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this, MainViewModelFactory(app.repository))[MainViewModel::class.java]
    }

    private lateinit var historyAdapter: HistoryAdapter
    private val clipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // Permission request launcher for Audio & Notifications
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        updatePermissionCardVisibility()
        if (micGranted) {
            Toast.makeText(this, "Microphone permission granted!", Toast.LENGTH_SHORT).show()
        }
    }

    // Overlay permission launcher
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionCardVisibility()
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission granted!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupApiKeyCard()
        setupServiceToggle()
        setupPermissionCard()
        observeHistory()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionCardVisibility()
        syncSwitchState()
        checkCrashDiagnostics()
    }

    private fun checkCrashDiagnostics() {
        val lastCrash = app.getLastCrash()
        if (!lastCrash.isNullOrBlank()) {
            app.clearLastCrash()
            AlertDialog.Builder(this)
                .setTitle("MillMitra Diagnostic Notice")
                .setMessage("Previous error details:\n\n${lastCrash.take(600)}")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(
            onCopyClick = { textToCopy ->
                val clip = ClipData.newPlainText("MillMitra", textToCopy)
                clipboardManager.setPrimaryClip(clip)
                Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
            },
            onWhatsAppClick = { textToShare ->
                shareToWhatsApp(textToShare)
            },
            onDeleteClick = { entryId ->
                viewModel.deleteEntry(entryId)
            }
        )

        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = historyAdapter
        }

        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Are you sure you want to delete all 7-day logs?")
                .setPositiveButton("Clear") { _, _ ->
                    viewModel.clearAllHistory()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun shareToWhatsApp(text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.whatsapp")
        }
        try {
            startActivity(sendIntent)
        } catch (e: Exception) {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Share Mill Report"
            )
            startActivity(chooser)
        }
    }

    private fun setupApiKeyCard() {
        val currentKey = app.getApiKey()
        updateApiKeyCardState(currentKey)

        binding.btnOpenSettings.setOnClickListener {
            showSettingsBottomSheet()
        }

        binding.btnSaveApiKey.setOnClickListener {
            val key = binding.etApiKey.text?.toString()?.trim().orEmpty()
            if (key.isNotBlank()) {
                app.setApiKey(key)
                updateApiKeyCardState(key)
                Toast.makeText(this, "Gemini API Key Saved & Connected!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter a valid Gemini API key", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnChangeApiKey.setOnClickListener {
            showSettingsBottomSheet()
        }
    }

    private fun updateApiKeyCardState(key: String) {
        if (key.isNotBlank() && key != "YOUR_GEMINI_API_KEY_HERE") {
            // Key is configured: hide the big card from the main screen for a clean, executive look
            binding.cardApiKey.visibility = View.GONE
        } else {
            // Key is missing/placeholder: show setup card so user can enter their API key
            binding.cardApiKey.visibility = View.VISIBLE
            binding.layoutApiKeyConnected.visibility = View.GONE
            binding.layoutApiKeyInput.visibility = View.VISIBLE
        }
    }

    private fun showSettingsBottomSheet() {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_settings, null)
        bottomSheet.setContentView(sheetView)

        val currentKey = app.getApiKey()
        val tvSheetMaskedKey = sheetView.findViewById<android.widget.TextView>(R.id.tvSheetMaskedKey)
        val etSheetApiKey = sheetView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSheetApiKey)
        val btnSheetSaveKey = sheetView.findViewById<android.widget.Button>(R.id.btnSheetSaveKey)
        val btnSheetClose = sheetView.findViewById<android.widget.ImageButton>(R.id.btnSheetClose)

        val masked = if (currentKey.length > 8 && currentKey != "YOUR_GEMINI_API_KEY_HERE") {
            "${currentKey.take(6)}•••••••••${currentKey.takeLast(4)}"
        } else {
            "Not Configured"
        }
        tvSheetMaskedKey.text = masked
        etSheetApiKey.setText(if (currentKey != "YOUR_GEMINI_API_KEY_HERE") currentKey else "")

        btnSheetClose.setOnClickListener {
            bottomSheet.dismiss()
        }

        btnSheetSaveKey.setOnClickListener {
            val newKey = etSheetApiKey.text?.toString()?.trim().orEmpty()
            if (newKey.isNotBlank()) {
                app.setApiKey(newKey)
                updateApiKeyCardState(newKey)
                Toast.makeText(this, "Gemini API Key Updated!", Toast.LENGTH_SHORT).show()
                bottomSheet.dismiss()
            } else {
                Toast.makeText(this, "Please enter a valid API key", Toast.LENGTH_SHORT).show()
            }
        }

        bottomSheet.show()
    }

    private fun setupServiceToggle() {
        binding.switchOverlayService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!MillMitraOverlayService.isRunning) {
                    if (!Settings.canDrawOverlays(this)) {
                        binding.switchOverlayService.isChecked = false
                        Toast.makeText(this, "Please enable 'Display Over Other Apps' to show the floating ball.", Toast.LENGTH_LONG).show()
                        requestOverlayPermission()
                        return@setOnCheckedChangeListener
                    }
                    startOverlayService()
                }
            } else {
                if (MillMitraOverlayService.isRunning) {
                    stopOverlayService()
                }
            }
        }
    }

    private fun setupPermissionCard() {
        binding.btnGrantOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        binding.btnGrantMic.setOnClickListener {
            requestMicPermission()
        }
    }

    private fun requestOverlayPermission() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun requestMicPermission() {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun updatePermissionCardVisibility() {
        val overlayAllowed = Settings.canDrawOverlays(this)
        val audioAllowed = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (overlayAllowed) {
            binding.tvOverlayStatus.text = "Status: ✅ Allowed"
            binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.mill_green))
            binding.btnGrantOverlay.visibility = View.GONE
        } else {
            binding.tvOverlayStatus.text = "Status: ❌ Not Allowed"
            binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_amber))
            binding.btnGrantOverlay.visibility = View.VISIBLE
        }

        if (audioAllowed) {
            binding.tvMicStatus.text = "Status: ✅ Allowed"
            binding.tvMicStatus.setTextColor(ContextCompat.getColor(this, R.color.mill_green))
            binding.btnGrantMic.visibility = View.GONE
        } else {
            binding.tvMicStatus.text = "Status: ❌ Not Allowed"
            binding.tvMicStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_amber))
            binding.btnGrantMic.visibility = View.VISIBLE
        }

        binding.cardPermissions.visibility = if (overlayAllowed && audioAllowed) View.GONE else View.VISIBLE
    }

    private fun startOverlayService() {
        val intent = Intent(this, MillMitraOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        binding.tvServiceStatus.text = "● Floating Ball: ON (Active on screen)"
        binding.tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.mill_emerald))
        Toast.makeText(this, "MillMitra floating assistant activated!", Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlayService() {
        val intent = Intent(this, MillMitraOverlayService::class.java)
        stopService(intent)
        binding.tvServiceStatus.text = "○ Floating Ball: OFF"
        binding.tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        Toast.makeText(this, "MillMitra overlay stopped", Toast.LENGTH_SHORT).show()
    }

    private fun syncSwitchState() {
        val isRunning = MillMitraOverlayService.isRunning
        if (binding.switchOverlayService.isChecked != isRunning) {
            binding.switchOverlayService.isChecked = isRunning
        }
        if (isRunning) {
            binding.tvServiceStatus.text = "● Floating Ball: ON (Active on screen)"
            binding.tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.mill_emerald))
        } else {
            binding.tvServiceStatus.text = "○ Floating Ball: OFF"
            binding.tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        }
    }

    private fun observeHistory() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.historyList.collect { list ->
                    historyAdapter.submitList(list)
                    if (list.isEmpty()) {
                        binding.cardEmptyState.visibility = View.VISIBLE
                        binding.rvHistory.visibility = View.GONE
                    } else {
                        binding.cardEmptyState.visibility = View.GONE
                        binding.rvHistory.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}
