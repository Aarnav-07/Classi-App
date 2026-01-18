package com.example.classi_backend

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat

class ImageMonitorService : Service() {

    private lateinit var contentObserver: ContentObserver
    private val detectedImages = ArrayList<Uri>()
    private var lastImageId: Long = -1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createForegroundNotification()
        startForeground(1, notification)
        
        lastImageId = getLatestImageId()
        registerImageObserver()
    }

    private fun registerImageObserver() {
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                
                val currentLatestId = getLatestImageId()
                if (currentLatestId != -1L && currentLatestId != lastImageId) {
                    lastImageId = currentLatestId
                    val imageUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
                        currentLatestId.toString()
                    )
                    
                    detectedImages.add(imageUri)
                    updateSummaryNotification()
                }
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    private fun getLatestImageId(): Long {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return -1L
    }

    private fun updateSummaryNotification() {
        if (detectedImages.isEmpty()) return

        val count = detectedImages.size
        val title = if (count == 1) "1 new image observed" else "$count new images observed"
        
        val deleteIntent = Intent(this, NotificationDeleteReceiver::class.java)
        val deletePendingIntent = PendingIntent.getBroadcast(
            this, 0, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Launch GalleryActivity with the list of URIs
        val galleryIntent = Intent(this, GalleryActivity::class.java).apply {
            putParcelableArrayListExtra("image_uris", detectedImages)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, galleryIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Tap to view all detected images")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(SUMMARY_NOTIF_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLEAR_IMAGES) {
            detectedImages.clear()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(contentObserver)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Image Monitor Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Monitoring Images")
            .setContentText("Watching for new images...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "ImageMonitorChannel"
        const val SUMMARY_NOTIF_ID = 2
        const val ACTION_CLEAR_IMAGES = "com.example.classi_backend.CLEAR_IMAGES"
    }
}
