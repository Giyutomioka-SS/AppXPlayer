package com.example.appxplayer

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.example.appxplayer.databinding.ActivityPlayerBinding

private const val TAG = "PlayerActivity"
private const val STATE_RESUME_WINDOW = "resumeWindow"
private const val STATE_RESUME_POSITION = "resumePosition"
private const val STATE_PLAYER_FULLSCREEN = "playerFullscreen"
private const val STATE_PLAYER_PLAYING = "playerOnPlay"

@UnstableApi
class PlayerActivity : AppCompatActivity(), SessionAvailabilityListener {

    private val viewBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityPlayerBinding.inflate(layoutInflater)
    }

    private lateinit var castContext: CastContext
    private lateinit var castPlayer: CastPlayer
    private var exoPlayer: ExoPlayer? = null
    private var player: Player? = null
    private var playWhenReady = true
    private var mediaItemIndex = 0
    private var playbackPosition = 0L
    private var isFullscreen = false
    private var isLock = false
    private lateinit var imageViewLock: ImageView
    private val playbackStateListener: Player.Listener = PlayerEvent.playbackStateListener(TAG)
    private val windowInsetsController by lazy {
        WindowInsetsControllerCompat(window, window.decorView)
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        setFindViewById()

        // Initialize CastContext and CastPlayer
        castContext = CastContext.getSharedInstance(this)
        castPlayer = CastPlayer(castContext)
        castPlayer.setSessionAvailabilityListener(this)

        Log.d(TAG, "Cast initialization status: ${castContext.castState}")

        // Set up MediaRouteButton
        findViewById<MediaRouteButton>(R.id.exo_cast)?.apply {
            CastButtonFactory.setUpMediaRouteButton(context, this)
            dialogFactory = CustomCastThemeFactory()
        }

        // Set the action bar color
        supportActionBar?.setBackgroundDrawable(
            ContextCompat.getDrawable(this, R.color.purple_700)
        )

        // Restore state from savedInstanceState if available
        if (savedInstanceState != null) {
            mediaItemIndex = savedInstanceState.getInt(STATE_RESUME_WINDOW)
            playbackPosition = savedInstanceState.getLong(STATE_RESUME_POSITION)
            isFullscreen = savedInstanceState.getBoolean(STATE_PLAYER_FULLSCREEN)
            playWhenReady = savedInstanceState.getBoolean(STATE_PLAYER_PLAYING)
        } else {
            showSystemUi()
        }

        setLockScreen()
    }

    // FindViewById for imageViewLock
    private fun setFindViewById() {
        imageViewLock = findViewById(R.id.imageViewLock)
    }

    // Initialize ExoPlayer instance and prepare media playback
    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(this)
            .build()
            .also { player ->
                viewBinding.videoView.player = player
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSizeSd()
                    .build()

                // Set up media item with subtitle configuration
                val mediaItem = MediaItem.Builder()
                    .setUri(getString(R.string.media_url_mp4))
                    .setMimeType(MimeTypes.APPLICATION_MP4)
                    .build()

                val subtitle = MediaItem.SubtitleConfiguration.Builder(Uri.parse(getString(R.string.subtitle_url)))
                    .setMimeType(MimeTypes.TEXT_VTT) // Change as per your subtitle format
                    .setLanguage("en") // Change as per your subtitle language
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()

                val mediaItemWithSubtitle = mediaItem.buildUpon()
                    .setSubtitleConfigurations(listOf(subtitle))
                    .build()

                // Add media item to player, set playback settings
                player.addMediaItem(mediaItemWithSubtitle)
                player.playWhenReady = playWhenReady
                player.seekTo(mediaItemIndex, playbackPosition)
                player.addListener(playbackStateListener)
                player.prepare()
            }

        // Set click listener for fullscreen button
        viewBinding.videoView.setFullscreenButtonClickListener {
            Log.e(TAG, "isFullscreen: $it")
            if (it) {
                openFullscreen()
            } else {
                closeFullscreen()
            }
        }

        // If already in fullscreen, open fullscreen mode
        if (isFullscreen) openFullscreen()
    }

    // Release ExoPlayer instance
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

    // Start CastPlayer when Cast session is available
    private fun startCastPlayer() {
        castPlayer.setMediaItem(MediaItem.Builder().setUri(getString(R.string.media_url_mp4)).setMimeType(MimeTypes.APPLICATION_MP4).build())
        castPlayer.prepare()
        viewBinding.videoView.player = castPlayer
        exoPlayer?.stop()
    }

    // Start ExoPlayer when Cast session is unavailable
    private fun startExoPlayer() {
        exoPlayer?.setMediaItem(MediaItem.Builder().setUri(getString(R.string.media_url_mp4)).setMimeType(MimeTypes.APPLICATION_MP4).build())
        exoPlayer?.prepare()
        viewBinding.videoView.player = exoPlayer
        castPlayer.stop()
    }

    // Listener for Cast session availability changes

    override fun onCastSessionAvailable() {
        startCastPlayer()
    }

    override fun onCastSessionUnavailable() {
        startExoPlayer()
    }

    // Enter fullscreen mode
    private fun openFullscreen() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val params: FrameLayout.LayoutParams = viewBinding.videoView.layoutParams as FrameLayout.LayoutParams
        params.width = FrameLayout.LayoutParams.MATCH_PARENT
        params.height = FrameLayout.LayoutParams.MATCH_PARENT
        viewBinding.videoView.layoutParams = params
        hideSystemUi()
        isFullscreen = true
    }

    // Exit fullscreen mode
    private fun closeFullscreen() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val params: FrameLayout.LayoutParams = viewBinding.videoView.layoutParams as FrameLayout.LayoutParams
        params.width = FrameLayout.LayoutParams.MATCH_PARENT
        params.height = FrameLayout.LayoutParams.MATCH_PARENT
        viewBinding.videoView.layoutParams = params
        showSystemUi()
        isFullscreen = false
    }

    // Hide system UI (status bar and navigation bar)
    private fun hideSystemUi() {
        supportActionBar?.hide()
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // Show system UI (status bar and navigation bar)
    private fun showSystemUi() {
        supportActionBar?.show()
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }

    // Set lock screen functionality
    private fun setLockScreen() {
        imageViewLock.setOnClickListener {
            if (!isLock) {
                imageViewLock.setImageDrawable(
                    ContextCompat.getDrawable(
                        applicationContext,
                        R.drawable.ic_baseline_lock
                    )
                )
            } else {
                imageViewLock.setImageDrawable(
                    ContextCompat.getDrawable(
                        applicationContext,
                        R.drawable.ic_baseline_lock_open
                    )
                )
            }
            isLock = !isLock
            lockScreen(isLock)
        }
    }

    // Lock or unlock screen controls based on lock status
    private fun lockScreen(lock: Boolean) {
        if (lock) {
            // Hide all controls except the lock icon
            viewBinding.videoView.useController = false
            imageViewLock.visibility = View.VISIBLE
            viewBinding.exoCast.visibility = View.INVISIBLE
            if (!isFullscreen) {
                hideSystemUi()
            }
        } else {
            // Show all controls including the lock icon
            viewBinding.videoView.useController = true
            imageViewLock.visibility = View.VISIBLE
            viewBinding.exoCast.visibility = View.VISIBLE
            if (!isFullscreen) {
                showSystemUi()
            }
        }
    }

    // Save instance state for orientation changes
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_RESUME_WINDOW, player?.currentMediaItemIndex ?: 0)
        outState.putLong(STATE_RESUME_POSITION, player?.currentPosition ?: 0)
        outState.putBoolean(STATE_PLAYER_FULLSCREEN, isFullscreen)
        outState.putBoolean(STATE_PLAYER_PLAYING, playWhenReady)
        super.onSaveInstanceState(outState)
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
}
