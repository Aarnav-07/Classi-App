package com.example.classi_backend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationDeleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, ImageMonitorService::class.java).apply {
            action = ImageMonitorService.ACTION_CLEAR_IMAGES
        }
        context.startService(serviceIntent)
    }
}
