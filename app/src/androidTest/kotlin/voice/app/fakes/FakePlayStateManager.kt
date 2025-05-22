package voice.app.fakes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import voice.playback.playstate.PlayStateManager // Import the real one
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Match scope of real one if Dagger provides it as Singleton
class FakePlayStateManager @Inject constructor() : PlayStateManager() { // Now extends PlayStateManager
    private val _overridePlayState = MutableStateFlow(PlayState.Paused)
    override val flow: StateFlow<PlayState> get() = _overridePlayState
    override var playState: PlayState
        get() = _overridePlayState.value
        set(value) {
            _overridePlayState.value = value
            println("FakePlayStateManager: State set to $value")
        }
}
