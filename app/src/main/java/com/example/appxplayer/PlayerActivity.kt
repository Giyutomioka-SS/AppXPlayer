package com.example.appxplayer

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.appxplayer.databinding.ActivityPlayerBinding

private const val TAG = "PlayerActivity"
private const val STATE_RESUME_WINDOW = "resumeWindow"
private const val STATE_RESUME_POSITION = "resumePosition"
private const val STATE_PLAYER_FULLSCREEN = "playerFullscreen"
private const val STATE_PLAYER_PLAYING = "playerOnPlay"

class PlayerActivity : AppCompatActivity() {

    private val viewBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityPlayerBinding.inflate(layoutInflater)
    }

    private var player: Player? = null
    private var playWhenReady = true
    private var mediaItemIndex = 0
    private var playbackPosition = 0L
    private var isFullscreen = false
    private val playbackStateListener: Player.Listener = PlayerEvent.playbackStateListener(TAG)
    private val windowInsetsController by lazy {
        WindowInsetsControllerCompat(window, window.decorView)
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this)
            .build()
            .also { exoPlayer ->
                viewBinding.videoView.player = exoPlayer
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSizeSd()
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(getString(R.string.media_url_mp4))
                    .setMimeType(MimeTypes.APPLICATION_MP4)
                    .build()
                exoPlayer.addMediaItem(mediaItem)
                exoPlayer.playWhenReady = playWhenReady
                exoPlayer.seekTo(mediaItemIndex, playbackPosition)
                exoPlayer.addListener(playbackStateListener)
                exoPlayer.prepare()
            }

        viewBinding.videoView.setFullscreenButtonClickListener {
            Log.e(TAG, "isFullscreen: $it")
            if (it) {
                openFullscreen()
            } else {
                closeFullscreen()
            }
        }

        if (isFullscreen) openFullscreen()
    }

    private fun releasePlayer() {
        viewBinding.videoView.setFullscreenButtonClickListener(null)
        player?.run {
            playbackPosition = this.currentPosition
            mediaItemIndex = this.currentMediaItemIndex
            playWhenReady = this.playWhenReady
            removeListener(playbackStateListener)
            release()
        }
        player = null
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun openFullscreen() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val params: FrameLayout.LayoutParams = viewBinding.videoView.layoutParams as FrameLayout.LayoutParams
        params.width = FrameLayout.LayoutParams.MATCH_PARENT
        params.height = FrameLayout.LayoutParams.MATCH_PARENT
        viewBinding.videoView.layoutParams = params
        hideSystemUi()
        isFullscreen = true
    }

    private fun closeFullscreen() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val params: FrameLayout.LayoutParams = viewBinding.videoView.layoutParams as FrameLayout.LayoutParams
        params.width = FrameLayout.LayoutParams.MATCH_PARENT
        params.height = FrameLayout.LayoutParams.MATCH_PARENT
        viewBinding.videoView.layoutParams = params
        showSystemUi()
        isFullscreen = false
    }

    private fun hideSystemUi() {
        supportActionBar?.hide()
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun showSystemUi() {
        supportActionBar?.show()
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_RESUME_WINDOW, player?.currentMediaItemIndex ?: 0)
        outState.putLong(STATE_RESUME_POSITION, player?.currentPosition ?: 0)
        outState.putBoolean(STATE_PLAYER_FULLSCREEN, isFullscreen)
        outState.putBoolean(STATE_PLAYER_PLAYING, playWhenReady)
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        // Set the action bar color
        supportActionBar?.setBackgroundDrawable(
            ContextCompat.getDrawable(this, R.color.purple_700)
        )

        if (savedInstanceState != null) {
            mediaItemIndex = savedInstanceState.getInt(STATE_RESUME_WINDOW)
            playbackPosition = savedInstanceState.getLong(STATE_RESUME_POSITION)
            isFullscreen = savedInstanceState.getBoolean(STATE_PLAYER_FULLSCREEN)
            playWhenReady = savedInstanceState.getBoolean(STATE_PLAYER_PLAYING)
        } else {
            showSystemUi()
        }
    }

    public override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    public override fun onResume() {
        super.onResume()
        if (player == null) {
            initializePlayer()
        }
    }

    public override fun onPause() {
        super.onPause()
    }

    public override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onBackPressed() {
        if (isFullscreen) {
            closeFullscreen()
            return
        }
        super.onBackPressed()
    }
}
