package com.example.classi_backend

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var serviceToggle: SwitchCompat
    private lateinit var folderText: TextView
    private lateinit var selectFolderButton: Button
    
    // Deep Scan UI
    private lateinit var deepScanButton: Button
    private lateinit var scanStatusText: TextView
    private lateinit var scanProgressBar: ProgressBar

    private var isScanning = false
    private var scanJob: Job? = null
    private val detectedUris = mutableListOf<Uri>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // Check which action triggered the permission request
            if (isScanning) {
                startAlbumSelection()
            } else {
                startMonitoringService()
                serviceToggle.isChecked = true
            }
        } else {
            Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show()
            serviceToggle.isChecked = false
            isScanning = false
            updateScanUI()
        }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            saveSelectedFolder(it.toString())
            updateFolderUI(it.toString())
            serviceToggle.isEnabled = true
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
        
        deepScanButton = findViewById(R.id.deep_scan_button)
        scanStatusText = findViewById(R.id.scan_status_text)
        scanProgressBar = findViewById(R.id.scan_progress)

        val savedFolder = getSavedFolder()
        updateFolderUI(savedFolder)

        serviceToggle.isEnabled = savedFolder != null
        serviceToggle.isChecked = isServiceRunning(ImageMonitorService::class.java)

        serviceToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkPermissionsAndRun { startMonitoringService() }
            } else {
                stopMonitoringService()
            }
        }

        selectFolderButton.setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        deepScanButton.setOnClickListener {
            if (isScanning) {
                stopDeepScan()
            } else {
                checkPermissionsAndRun { startAlbumSelection() }
            }
        }
    }

    private fun checkPermissionsAndRun(action: () -> Unit) {
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
            action()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun startAlbumSelection() {
        val albums = fetchAlbums()
        if (albums.isEmpty()) {
            Toast.makeText(this, "No albums found", Toast.LENGTH_SHORT).show()
            return
        }

        val albumNames = albums.keys.toTypedArray()
        val checkedItems = BooleanArray(albumNames.size) { false }
        val selectedAlbums = mutableListOf<String>()

        AlertDialog.Builder(this)
            .setTitle("Select Albums to Scan")
            .setMultiChoiceItems(albumNames, checkedItems) { _, which, isChecked ->
                if (isChecked) selectedAlbums.add(albumNames[which])
                else selectedAlbums.remove(albumNames[which])
            }
            .setPositiveButton("Start Scan") { _, _ ->
                if (selectedAlbums.isNotEmpty()) {
                    val bucketIds = selectedAlbums.mapNotNull { albums[it] }
                    startDeepScan(bucketIds)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchAlbums(): Map<String, String> {
        val albums = mutableMapOf<String, String>()
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID
        )
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol)
                val id = cursor.getString(idCol)
                if (name != null && id != null) albums[name] = id
            }
        }
        return albums
    }

    private fun startDeepScan(bucketIds: List<String>) {
        isScanning = true
        detectedUris.clear()
        updateScanUI()
        
        scanJob = lifecycleScope.launch {
            val analyzer = ImageAnalyzer(this@MainActivity)
            val imageUris = getImagesInBuckets(bucketIds)
            
            scanProgressBar.max = imageUris.size
            scanProgressBar.progress = 0
            
            withContext(Dispatchers.Default) {
                for ((index, uri) in imageUris.withIndex()) {
                    if (!isScanning) break
                    
                    if (analyzer.analyzeImage(uri)) {
                        detectedUris.add(uri)
                    }
                    
                    withContext(Dispatchers.Main) {
                        scanProgressBar.progress = index + 1
                        scanStatusText.text = "Scanning image ${index + 1} of ${imageUris.size}..."
                    }
                }
            }
            
            analyzer.close()
            finishScan()
        }
    }

    private fun getImagesInBuckets(bucketIds: List<String>): List<Uri> {
        val uris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.BUCKET_ID} IN (${bucketIds.joinToString { "?" }})"
        
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            bucketIds.toTypedArray(),
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                uris.add(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()))
            }
        }
        return uris
    }

    private fun stopDeepScan() {
        isScanning = false
        scanJob?.cancel()
        finishScan()
    }

    private fun finishScan() {
        isScanning = false
        updateScanUI()
        
        if (detectedUris.isNotEmpty()) {
            val intent = Intent(this, GalleryActivity::class.java).apply {
                putParcelableArrayListExtra("image_uris", ArrayList(detectedUris))
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "No candidates found", Toast.LENGTH_SHORT).show()
        }
        
        scanStatusText.text = "Ready to scan"
        scanProgressBar.visibility = View.GONE
    }

    private fun updateScanUI() {
        deepScanButton.text = if (isScanning) "Stop Deep Scan" else "Start Deep Scan"
        scanProgressBar.visibility = if (isScanning) View.VISIBLE else View.GONE
        if (isScanning) scanStatusText.text = "Starting scan..."
    }

    private fun updateFolderUI(uriString: String?) {
        folderText.text = if (uriString != null) {
            val uri = uriString.toUri()
            "Destination: ${uri.path}"
        } else {
            "No folder selected"
        }
    }

    private fun saveSelectedFolder(uriString: String) {
        getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit {
            putString("destination_folder", uriString)
        }
    }

    private fun getSavedFolder(): String? {
        return getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            .getString("destination_folder", null)
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
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
