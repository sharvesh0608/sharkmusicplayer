package com.shapps.sharkmusicplayer.exoplayer

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.shapps.sharkmusicplayer.exoplayer.callbacks.MusicPlaybackPreparer
import com.shapps.sharkmusicplayer.exoplayer.callbacks.MusicPlayerEventsListener
import com.shapps.sharkmusicplayer.exoplayer.callbacks.MusicPlayerNotificationListener
import com.shapps.sharkmusicplayer.other.Constants.MEDIA_ROOT_ID
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

private const val SERVICE_TAG = "MusicService"

@AndroidEntryPoint
class MusicService : MediaBrowserServiceCompat() {

    @Inject
    lateinit var dataSourceFactory: DefaultDataSourceFactory

    @Inject
    lateinit var exoPlayer: SimpleExoPlayer

    @Inject
    lateinit var firebaseMusicSource: FirebaseMusicSource

    private lateinit var musicNotificationManager: MusicNotificationManager

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaSessionConnector: MediaSessionConnector
    private lateinit var musicPlayerEventListener:MusicPlayerEventsListener
    var isForegroundService =false

    private var currentPlayingSong: MediaMetadataCompat?=null
    private var isPlayerInitialized = false

    companion object{
        var curSongDuration =0L
          private set
    }

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            firebaseMusicSource.fetechMediaData()
        }
        val activityIntent = packageManager?.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, 0)
        }
        mediaSession = MediaSessionCompat(this, SERVICE_TAG).apply {
            setSessionActivity(activityIntent)
            isActive = true
        }

        sessionToken = mediaSession.sessionToken
        musicNotificationManager= MusicNotificationManager(
                this,mediaSession.sessionToken,
            MusicPlayerNotificationListener(this)
                ){
                       curSongDuration=exoPlayer.duration
        }
        val musicPlaybackPreparer=MusicPlaybackPreparer(firebaseMusicSource) {
            currentPlayingSong=it
            preparePlayer(
               firebaseMusicSource.songs,it,true
            )
        }

        mediaSessionConnector = MediaSessionConnector(mediaSession)
        mediaSessionConnector.setPlaybackPreparer(musicPlaybackPreparer)
        mediaSessionConnector.setQueueNavigator(MusicQueueNavigator())
        mediaSessionConnector.setPlayer(exoPlayer)
        musicPlayerEventListener =MusicPlayerEventsListener(this)
        exoPlayer.addListener(musicPlayerEventListener)
        musicNotificationManager.showNotification(exoPlayer)
    }


    private inner class MusicQueueNavigator: TimelineQueueNavigator(mediaSession){
        override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {
            return firebaseMusicSource.songs[windowIndex].description
        }
    }

    private fun preparePlayer(
          songs: List<MediaMetadataCompat>,
          itemToPlay: MediaMetadataCompat?,
          playNow: Boolean
    ){
            val curSongIndex =if (currentPlayingSong==null) 0 else songs.indexOf(itemToPlay)
            exoPlayer.prepare(firebaseMusicSource.asMediaSource(dataSourceFactory))
        exoPlayer.seekTo(curSongIndex,0L)
        exoPlayer.playWhenReady=playNow
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        exoPlayer.stop()
    }
   //managing clients connect or deney
    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
       return BrowserRoot(MEDIA_ROOT_ID,null)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        exoPlayer.release()
        exoPlayer.removeListener(musicPlayerEventListener)
    }

    override fun onLoadChildren(
        parentId: String,//call so that we can get list of songs
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
         when(parentId){
             MEDIA_ROOT_ID ->{
                 val resutlsSent =firebaseMusicSource.whenReady { isInitialized ->
                     if(isInitialized){
                         result.sendResult(firebaseMusicSource.asMediaItems())
                       if(!isPlayerInitialized){
                           preparePlayer(firebaseMusicSource.songs,firebaseMusicSource.songs[0],false)
                           isPlayerInitialized=true
                       }
                     }else{
                          result.sendResult(null)
                     }

                 }
                 if(!resutlsSent){
                       result.detach()
                 }
             }
         }
    }
}