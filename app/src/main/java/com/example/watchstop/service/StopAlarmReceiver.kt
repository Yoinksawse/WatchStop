package com.example.watchstop.service

import android.app.Service.START_STICKY
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val stopIntent = Intent(context, GeofenceMonitorService::class.java)
        stopIntent.action = "ACTION_STOP_ALARM"
        context.startService(stopIntent)
    }
}