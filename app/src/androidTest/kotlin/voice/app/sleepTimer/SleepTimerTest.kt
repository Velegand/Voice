package voice.app.sleepTimer

import android.content.Context
import androidx.datastore.core.DataStore
// androidx.datastore.preferences.core.PreferenceDataStoreFactory (no longer needed here)
// androidx.datastore.preferences.core.Preferences (no longer needed here)
// androidx.datastore.preferences.preferencesDataStoreFile (no longer needed here)
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeMediaSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers // Keep for MainCoroutineRule if used there
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.Description // Keep for MainCoroutineRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement // Keep for MainCoroutineRule
// import voice.playback.PlayerController // No longer needed directly
// import voice.playback.playstate.PlayStateManager // No longer needed directly
import voice.sleepTimer.ShakeDetector
import voice.sleepTimer.SleepTimer
import kotlin.time.Duration
import voice.app.TestApp // Added
import voice.app.fakes.FakePlayerController // Ensure this is imported if not already
import voice.app.fakes.FakePlayStateManager // Ensure this is imported
import voice.app.injection.TestAppComponent // Added
import voice.common.pref.SleepTimeStore // Added
import javax.inject.Inject // Added
import kotlinx.coroutines.runBlocking // Added
import kotlin.time.Duration.Companion.minutes // For default value example
import org.junit.Test // Already present, but good to confirm
import kotlinx.coroutines.flow.take // Added for new test
import kotlinx.coroutines.flow.toList // Added for new test
import kotlinx.coroutines.launch // Added for new test
import kotlin.test.assertEquals // Added for new test (Using kotlin.test assertions)
import kotlin.test.assertFalse // Added for new test
import kotlin.test.assertTrue // Added for new test
import kotlin.time.Duration.Companion.seconds // Added for new test

@ExperimentalCoroutinesApi
class MainCoroutineRule(val testDispatcher: TestDispatcher = StandardTestDispatcher(TestCoroutineScheduler())) : TestWatcher() {
    override fun starting(description: Description) {
        super.starting(description)
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        super.finished(description)
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SleepTimerTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(StandardTestDispatcher(TestCoroutineScheduler()))

    private lateinit var context: Context
    private lateinit var testAppComponent: TestAppComponent
    private lateinit var fakeShakeDetector: FakeShakeDetector
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var fakeMediaSource: FakeMediaSource

    @Inject
    @SleepTimeStore
    lateinit var sleepTimeStore: DataStore<Int>

    private lateinit var fakePlayerController: FakePlayerController
    private lateinit var fakePlayStateManager: FakePlayStateManager

    private lateinit var sleepTimer: SleepTimer

    class FakeShakeDetector(context: Context, threshold: Float, coolDownTimeMs: Int) : ShakeDetector(context, threshold, coolDownTimeMs) {
        private val flow = MutableSharedFlow<Unit>(replay = 1)
        suspend fun emitShake() { flow.tryEmit(Unit) }
        override suspend fun detect() { flow.first() }
        override fun start(threshold: Float, coolDownTimeMs: Int) { /* No-op */ }
        override fun stop() { /* No-op */ }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val app = context.applicationContext as TestApp
        testAppComponent = voice.app.injection.appComponent as TestAppComponent // appComponent is global

        testAppComponent.inject(this) // Injects sleepTimeStore

        fakePlayerController = testAppComponent.getFakePlayerController()
        fakePlayStateManager = testAppComponent.getFakePlayStateManager()

        fakeShakeDetector = FakeShakeDetector(context, 0f, 0)
            
        exoPlayer = ExoPlayer.Builder(context)
            .setLooper(mainCoroutineRule.testDispatcher.scheduler.looper)
            .build()
        fakeMediaSource = FakeMediaSource()

        sleepTimer = SleepTimer(
            playStateManager = fakePlayStateManager,
            shakeDetector = fakeShakeDetector,
            sleepTimeStore = sleepTimeStore,
            playerController = fakePlayerController
        )

        mainCoroutineRule.testDispatcher.scheduler.runCurrent() // Ensure prior tasks run
        runBlocking(mainCoroutineRule.testDispatcher) {
            sleepTimeStore.updateData { 20.minutes.inWholeMinutes.toInt() } // Set default, e.g. 20 minutes
        }
    }

    @After
    fun tearDown() {
        exoPlayer.release()
        // To ensure cleanup between tests, reset DataStore to a default value
        runBlocking(mainCoroutineRule.testDispatcher) {
            sleepTimeStore.updateData { 20.minutes.inWholeMinutes.toInt() } // Reset to default
        }
        // Cancel the scope/dispatcher of the rule to clean up coroutines
        // Ensure all jobs launched on this dispatcher are complete before finishing the test.
        // (mainCoroutineRule.testDispatcher as StandardTestDispatcher).scheduler.advanceUntilIdle() // Good practice
        mainCoroutineRule.testDispatcher.scheduler.cancel() // Cancel to stop further execution
    }

    // Test methods will be added here
    @Test
    fun basicTest() { // Keep the basic test to ensure setup works
        assert(true)
    }

    @Test
    fun testSleepTimer_startsAndPausesPlayback() = mainCoroutineRule.testDispatcher.runTest {
        val testSleepDuration = 15.seconds // Use a slightly longer duration to accommodate fade
        
        // Attempt to access private fadeOutDuration via reflection
        // If this fails, the test will error, and we might need a fallback (e.g., hardcoded value)
        val fadeOutDurationField = SleepTimer::class.java.getDeclaredField("fadeOutDuration")
        fadeOutDurationField.isAccessible = true
        val fadeOutDuration = fadeOutDurationField.get(sleepTimer) as Duration

        // Ensure playback is initially "playing"
        fakePlayStateManager.playState = voice.playback.playstate.PlayStateManager.PlayState.Playing // Qualified PlayState
        // Our FakePlayerController's play() method sets isPaused = false and playWhenReady = true
        fakePlayerController.play() 
        assertEquals(1.0f, fakePlayerController.volume, "Initial volume should be 1.0f")


        // Start the sleep timer
        sleepTimer.setActive(testSleepDuration)
        assertEquals(testSleepDuration, sleepTimer.leftSleepTimeFlow.value, "Timer should start with test duration")
        assertTrue(sleepTimer.sleepTimerActive(), "Sleep timer should be active after starting")

        // --- Test countdown before fade-out ---
        // Advance time to just before fade-out begins
        val timeBeforeFadeStart = testSleepDuration - fadeOutDuration - 1.seconds
        if (timeBeforeFadeStart > Duration.ZERO) {
            advanceTimeBy(timeBeforeFadeStart.inWholeMilliseconds)
            runCurrent() // Allow coroutines to process events
            assertEquals(testSleepDuration - timeBeforeFadeStart, sleepTimer.leftSleepTimeFlow.value, "Time left should be correct before fade")
            assertFalse(fakePlayerController.isPaused, "Player should still be playing before fade (isPaused false)")
            assertEquals(1.0f, fakePlayerController.volume, "Volume should be at max before fade out")
        } else {
            // This case handles if testSleepDuration is shorter than fadeOutDuration + 1s
            // No pre-fade period to test, proceed directly to fade
        }

        // --- Test during fade-out ---
        var lastVolume = fakePlayerController.volume
        var advancedDuringFade = 0L
        val totalFadeMillis = fadeOutDuration.inWholeMilliseconds
        val checkIntervalMillis = 500L // Check volume every 500ms during fade

        while(advancedDuringFade < totalFadeMillis && sleepTimer.leftSleepTimeFlow.value > Duration.ZERO) {
            val timeToAdvanceThisStep = kotlin.math.min(checkIntervalMillis, totalFadeMillis - advancedDuringFade)
            if (timeToAdvanceThisStep <= 0) break // Avoid advancing by zero or negative

            advanceTimeBy(timeToAdvanceThisStep)
            runCurrent()
            advancedDuringFade += timeToAdvanceThisStep

            val currentVolume = fakePlayerController.volume
            val timeLeft = sleepTimer.leftSleepTimeFlow.value

            if (timeLeft < fadeOutDuration && timeLeft > Duration.ZERO) { // Check only if still in fade period and not ended
                assertTrue(currentVolume < lastVolume || currentVolume == 0f,
                    "Volume should be decreasing or 0f during fade. Current: $currentVolume, Last: $lastVolume, TimeLeft: $timeLeft")
            }
            lastVolume = currentVolume
        }
        
        // Ensure the full testSleepDuration has passed
        val totalTimeAdvancedSoFar = timeBeforeFadeStart.coerceAtLeast(Duration.ZERO).inWholeMilliseconds + advancedDuringFade
        val remainingTimeForSleepDuration = testSleepDuration.inWholeMilliseconds - totalTimeAdvancedSoFar
        if (remainingTimeForSleepDuration > 0) {
            advanceTimeBy(remainingTimeForSleepDuration)
            runCurrent()
        }

        // Advance a small buffer to ensure all coroutines and final actions complete
        advanceTimeBy(500) // 500ms buffer
        runCurrent()

        // --- Assertions after timer completion ---
        assertEquals(Duration.ZERO, sleepTimer.leftSleepTimeFlow.value, "Timer should be at zero after completion")
        assertTrue(fakePlayerController.isPaused, "Player should be paused after timer ends")
        assertEquals(1.0f, fakePlayerController.volume, "Volume should be reset to 1.0f after timer completion")
        assertFalse(sleepTimer.sleepTimerActive(), "Sleep timer should be inactive after completion")
    }

    @Test
    fun testSleepTimer_canBeCancelled() = mainCoroutineRule.testDispatcher.runTest {
        val testSleepDuration = 20.seconds // Start with a longer duration

        // Ensure playback is initially "playing"
        fakePlayStateManager.playState = voice.playback.playstate.PlayStateManager.PlayState.Playing
        fakePlayerController.play() // Sets isPaused = false, playWhenReady = true
        assertEquals(1.0f, fakePlayerController.volume, "Initial volume should be 1.0f")

        // Start the sleep timer
        sleepTimer.setActive(testSleepDuration)
        assertEquals(testSleepDuration, sleepTimer.leftSleepTimeFlow.value, "Timer should start with test duration")
        assertTrue(sleepTimer.sleepTimerActive(), "Sleep timer should be active after starting")

        // Advance time by a bit, but not enough for the timer to finish
        val advanceDuration = 5.seconds
        advanceTimeBy(advanceDuration.inWholeMilliseconds)
        runCurrent() // Allow coroutines to process

        assertEquals(testSleepDuration - advanceDuration, sleepTimer.leftSleepTimeFlow.value, "Time left should have decreased")
        assertTrue(sleepTimer.sleepTimerActive(), "Sleep timer should still be active before cancellation")
        assertFalse(fakePlayerController.isPaused, "Player should still be playing before cancellation")

        // Cancel the sleep timer by calling setActive(false)
        // According to SleepTimer.kt, this calls a private cancel() method which should:
        // - Cancel the job
        // - Set leftSleepTime to Duration.ZERO
        // - Set playerController.setVolume(1F)
        sleepTimer.setActive(false)
        runCurrent() // Allow cancellation logic to process

        // Assertions after cancellation
        assertEquals(Duration.ZERO, sleepTimer.leftSleepTimeFlow.value, "Timer should be at zero after cancellation")
        assertFalse(sleepTimer.sleepTimerActive(), "Sleep timer should be inactive after cancellation")
        
        // Crucially, the player should not be paused by the timer if it was cancelled.
        // The FakePlayerController's isPaused state is only changed by pauseWithRewind or play.
        // setActive(false) in SleepTimer does not call pause methods on PlayerController.
        assertFalse(fakePlayerController.isPaused, "Player should NOT be paused by the timer if cancelled")
        assertEquals(1.0f, fakePlayerController.volume, "Volume should be reset to 1.0f after cancellation")

        // Let some more time pass to ensure the timer doesn't somehow resume or trigger a pause later
        advanceTimeBy(testSleepDuration.inWholeMilliseconds) // Advance well past original end time
        runCurrent()

        assertFalse(fakePlayerController.isPaused, "Player should remain not paused after cancellation and further time")
        assertEquals(Duration.ZERO, sleepTimer.leftSleepTimeFlow.value, "Timer should remain at zero after cancellation")
        assertFalse(sleepTimer.sleepTimerActive(), "Sleep timer should remain inactive after cancellation")
    }

    @Test
    fun testSleepTimer_shakeResetsTimer() = mainCoroutineRule.runTest {
        val initialSleepDuration = 15.seconds // Initial duration for the timer
        // Note: SleepTimer.kt has a hardcoded shakeToResetTime of 30.seconds.
        // Our test must ensure the shake happens within this window relative to when the timer countdown *actually* starts
        // and when withTimeout(shakeToResetTime) is launched in SleepTimer.

        // Ensure playback is initially "playing"
        fakePlayStateManager.playState = voice.playback.playstate.PlayStateManager.PlayState.Playing // Qualified for consistency
        fakePlayerController.play() // Sets isPaused = false, playWhenReady = true

        // Start the sleep timer. This internally starts the countdown and the shake detection timeout.
        sleepTimer.setActive(initialSleepDuration)
        assertEquals(initialSleepDuration, sleepTimer.leftSleepTimeFlow.value, "Timer should start with initial duration")
        assertTrue(sleepTimer.sleepTimerActive(), "Sleep timer should be active after starting")

        // Advance time by a small amount. This time should be:
        // 1. Less than initialSleepDuration (so timer is still running)
        // 2. Less than the shakeToResetTime (30s in SleepTimer) so shake is detected.
        val timeToAdvance = 5.seconds
        assertTrue(timeToAdvance < initialSleepDuration, "Time advanced should be less than sleep duration for this test logic")
        // assertTrue(timeToAdvance < 30.seconds, "Time advanced should be less than shake detection window for this test logic") // Implicitly true if initialSleepDuration is reasonably small

        advanceTimeBy(timeToAdvance.inWholeMilliseconds)
        runCurrent() // Allow coroutines to process, timer to count down

        assertEquals(initialSleepDuration - timeToAdvance, sleepTimer.leftSleepTimeFlow.value, "Timer should have counted down before shake")

        // Simulate a shake
        // FakeShakeDetector.emitShake() will cause the detect() suspend function to resume in SleepTimer
        fakeShakeDetector.emitShake()
        runCurrent() // Allow shake detection logic and subsequent timer reset to process

        // Assertions after shake:
        // SleepTimer logic:
        // 1. Logs "Shake detected. Reset sleep time"
        // 2. Calls playerController.play()
        // 3. Calls setActive(sleepTime) again with the original sleepTime.

        assertEquals(initialSleepDuration, sleepTimer.leftSleepTimeFlow.value, "Timer should reset to initial duration after shake")
        assertTrue(sleepTimer.sleepTimerActive(), "Sleep timer should remain active after shake reset")
        
        // playerController.play() is called by SleepTimer after shake.
        assertFalse(fakePlayerController.isPaused, "Player should be playing after shake reset")
        // setActive(sleepTime) also calls playerController.setVolume(1F)
        assertEquals(1.0f, fakePlayerController.volume, "Volume should be 1.0f after shake reset")

        // Let the timer run down completely to ensure it operates normally after a shake reset
        // Advance by the full initialSleepDuration from this point, plus a buffer.
        advanceTimeBy(initialSleepDuration.inWholeMilliseconds)
        runCurrent()
        advanceTimeBy(1000) // Buffer for any final processing
        runCurrent()


        assertEquals(Duration.ZERO, sleepTimer.leftSleepTimeFlow.value, "Timer should eventually reach zero after reset")
        assertTrue(fakePlayerController.isPaused, "Player should be paused after timer completes following a reset")
        assertFalse(sleepTimer.sleepTimerActive(), "Sleep timer should be inactive after completing post-reset")
    }
}
