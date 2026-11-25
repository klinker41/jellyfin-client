package com.makd.afinity.player

import android.app.Application
import android.content.Context
import android.media.AudioAttributes as PlatformAudioAttributes
import android.media.AudioManager
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioFocusManager
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AudioServiceAudioFocusTest {

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var playerControl: RecordingPlayerControl
    private lateinit var audioFocusManager: AudioFocusManager

    private class RecordingPlayerControl : AudioFocusManager.PlayerControl {
        val volumeMultipliers = mutableListOf<Float>()
        val playerCommands = mutableListOf<Int>()

        override fun setVolumeMultiplier(volumeMultiplier: Float) {
            volumeMultipliers += volumeMultiplier
        }

        override fun executePlayerCommand(playerCommand: Int) {
            playerCommands += playerCommand
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        audioManager = context.getSystemService(AudioManager::class.java)
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        playerControl = RecordingPlayerControl()
        audioFocusManager = AudioFocusManager(context, Looper.getMainLooper(), playerControl)
    }

    @After
    fun tearDown() {
        audioFocusManager.release()
    }

    @Test
    fun `ABS_AUDIO_ATTRIBUTES ducks instead of pausing on notification focus loss`() {
        assertDucksOnNotificationFocusLoss(AudioService.ABS_AUDIO_ATTRIBUTES)
    }

    @Test
    fun `MUSIC_AUDIO_ATTRIBUTES ducks instead of pausing on notification focus loss`() {
        assertDucksOnNotificationFocusLoss(AudioService.MUSIC_AUDIO_ATTRIBUTES)
    }

    private fun assertDucksOnNotificationFocusLoss(attributes: AudioAttributes) {
        audioFocusManager.setAudioAttributes(attributes)

        val command = audioFocusManager.updateAudioFocus(true, Player.STATE_READY)

        assertEquals(AudioFocusManager.PLAYER_COMMAND_PLAY_WHEN_READY, command)
        val lastRequest = shadowOf(audioManager).lastAudioFocusRequest
        assertNotNull(lastRequest)
        val platformRequest = lastRequest.audioFocusRequest
        assertNotNull(platformRequest)
        assertFalse(platformRequest.willPauseWhenDucked())
        assertEquals(
            PlatformAudioAttributes.USAGE_MEDIA,
            platformRequest.audioAttributes.usage,
        )

        dispatchAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

        assertTrue(playerControl.playerCommands.isEmpty())
        assertEquals(0.2f, audioFocusManager.volumeMultiplier, 0.001f)
        assertEquals(listOf(0.2f), playerControl.volumeMultipliers)

        dispatchAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertEquals(1.0f, audioFocusManager.volumeMultiplier, 0.001f)
        assertEquals(listOf(0.2f, 1.0f), playerControl.volumeMultipliers)
    }

    @Test
    fun `switching from ABS_AUDIO_ATTRIBUTES to MUSIC_AUDIO_ATTRIBUTES keeps ducking enabled`() {
        audioFocusManager.setAudioAttributes(AudioService.ABS_AUDIO_ATTRIBUTES)
        assertEquals(
            AudioFocusManager.PLAYER_COMMAND_PLAY_WHEN_READY,
            audioFocusManager.updateAudioFocus(true, Player.STATE_READY),
        )

        audioFocusManager.setAudioAttributes(AudioService.MUSIC_AUDIO_ATTRIBUTES)
        assertEquals(
            AudioFocusManager.PLAYER_COMMAND_PLAY_WHEN_READY,
            audioFocusManager.updateAudioFocus(true, Player.STATE_READY),
        )

        val platformRequest = shadowOf(audioManager).lastAudioFocusRequest.audioFocusRequest
        assertNotNull(platformRequest)
        assertFalse(platformRequest.willPauseWhenDucked())

        dispatchAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

        assertTrue(playerControl.playerCommands.isEmpty())
        assertEquals(0.2f, audioFocusManager.volumeMultiplier, 0.001f)
    }

    @Test
    fun `ABS_AUDIO_ATTRIBUTES still pauses on transient focus loss and stops on permanent loss`() {
        audioFocusManager.setAudioAttributes(AudioService.ABS_AUDIO_ATTRIBUTES)
        assertEquals(
            AudioFocusManager.PLAYER_COMMAND_PLAY_WHEN_READY,
            audioFocusManager.updateAudioFocus(true, Player.STATE_READY),
        )

        dispatchAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(
            listOf(AudioFocusManager.PLAYER_COMMAND_WAIT_FOR_CALLBACK),
            playerControl.playerCommands,
        )

        dispatchAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(
            listOf(
                AudioFocusManager.PLAYER_COMMAND_WAIT_FOR_CALLBACK,
                AudioFocusManager.PLAYER_COMMAND_PLAY_WHEN_READY,
            ),
            playerControl.playerCommands,
        )

        dispatchAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertEquals(
            listOf(
                AudioFocusManager.PLAYER_COMMAND_WAIT_FOR_CALLBACK,
                AudioFocusManager.PLAYER_COMMAND_PLAY_WHEN_READY,
                AudioFocusManager.PLAYER_COMMAND_DO_NOT_PLAY,
            ),
            playerControl.playerCommands,
        )
    }

    private fun dispatchAudioFocusChange(focusChange: Int) {
        val listener = shadowOf(audioManager).lastAudioFocusRequest.listener
        assertNotNull(listener)
        listener.onAudioFocusChange(focusChange)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
