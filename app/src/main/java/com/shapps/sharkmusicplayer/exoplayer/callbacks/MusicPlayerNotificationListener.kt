package com.shapps.sharkmusicplayer.exoplayer.callbacks

import android.app.Notification
import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.shapps.sharkmusicplayer.exoplayer.MusicService
import com.shapps.sharkmusicplayer.other.Constants.NOTIFICATION_ID

class MusicPlayerNotificationListener(
    private val musicservice: MusicService

): PlayerNotificationManager.NotificationListener {
    override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
        super.onNotificationCancelled(notificationId, dismissedByUser)
        musicservice.apply{
            stopForeground(true)
            isForegroundService=false
            stopSelf()
        }
    }

    override fun onNotificationPosted(
        notificationId: Int,
        notification: Notification,
        ongoing: Boolean
    ) {
        super.onNotificationPosted(notificationId, notification, ongoing)
        musicservice.apply {
            if(ongoing&&! isForegroundService){
                ContextCompat.startForegroundService(
                    this, Intent( applicationContext,this::class.java)
                )
                startForeground(NOTIFICATION_ID,notification)
                isForegroundService=true
            }
        } }

}