package voice.app.fakes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
// import voice.playback.PlayerController // Not extending for now
import voice.common.BookId // May be needed if methods expect it
import voice.data.ChapterId // May be needed
import voice.playback.misc.Decibel
import javax.inject.Inject
import kotlin.time.Duration

// As per instructions, not extending PlayerController due to constructor complexities.
// The Dagger module will be responsible for providing this fake as a PlayerController.
class FakePlayerController @Inject constructor() {
    private var _volume: Float = 1.0f
    val volume: Float get() = _volume

    private var _isPaused: Boolean = true
    val isPaused: Boolean get() = _isPaused
    
    private var _playWhenReady: Boolean = false
    val playWhenReady: Boolean get() = _playWhenReady

    fun setVolume(vol: Float) {
        _volume = vol.coerceIn(0f, 1f)
        println("FakePlayerController: Volume set to $_volume")
    }

    fun pauseWithRewind(rewind: Duration) {
        _isPaused = true
        _playWhenReady = false
        println("FakePlayerController: Paused with rewind $rewind")
    }

    fun play() {
        _isPaused = false
        _playWhenReady = true
        println("FakePlayerController: Play called")
    }

    // Add other methods if SleepTimer uses them.
    // For `suspendUntilPlaying` in SleepTimer, this fake needs to interact with FakePlayStateManager
    // or have its own notion of "playing". Let's assume FakePlayStateManager handles this.
}
