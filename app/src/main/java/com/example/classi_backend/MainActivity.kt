package com.example.classi_backend

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var serviceToggle: SwitchCompat
    private lateinit var folderText: TextView
    private lateinit var selectFolderButton: Button

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startMonitoringService()
            serviceToggle.isChecked = true
        } else {
            Toast.makeText(this, "Permissions required for monitoring", Toast.LENGTH_LONG).show()
            serviceToggle.isChecked = false
        }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Persist access to this folder
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            saveSelectedFolder(it.toString())
            updateFolderUI(it.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        serviceToggle = findViewById(R.id.service_toggle)
        folderText = findViewById(R.id.selected_folder_text)
        selectFolderButton = findViewById(R.id.select_folder_button)

        // Load saved folder
        val savedFolder = getSavedFolder()
        updateFolderUI(savedFolder)

        serviceToggle.isChecked = isServiceRunning(ImageMonitorService::class.java)

        serviceToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkPermissionsAndStartService()
            } else {
                stopMonitoringService()
            }
        }

        selectFolderButton.setOnClickListener {
            folderPickerLauncher.launch(null)
        }
    }

    private fun updateFolderUI(uriString: String?) {
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            folderText.text = "Destination: ${uri.path}"
        } else {
            folderText.text = "No folder selected"
        }
    }

    private fun saveSelectedFolder(uriString: String) {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("destination_folder", uriString).apply()
    }

    private fun getSavedFolder(): String? {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return prefs.getString("destination_folder", null)
    }

    private fun checkPermissionsAndStartService() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isEmpty()) {
            startMonitoringService()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun startMonitoringService() {
        val intent = Intent(this, ImageMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopMonitoringService() {
        val intent = Intent(this, ImageMonitorService::class.java)
        stopService(intent)
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
