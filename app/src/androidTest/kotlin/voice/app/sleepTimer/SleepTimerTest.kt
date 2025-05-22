package voice.app.sleepTimer

import android.content.Context
import androidx.datastore.core.DataStore
// androidx.datastore.preferences.core.PreferenceDataStoreFactory (no longer needed here)
// androidx.datastore.preferences.core.Preferences (no longer needed here)
// androidx.datastore.preferences.preferencesDataStoreFile (no longer needed here)
import android.app.Application // Added
import android.content.Intent // Added for PlaybackService
import androidx.media3.exoplayer.ExoPlayer
// import androidx.media3.test.utils.FakeMediaSource // Removed for now
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule // Added
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
import voice.playback.PlayerController // Added for real injection
import voice.playback.playstate.PlayStateManager // Added for real injection
import voice.sleepTimer.ShakeDetector
import voice.sleepTimer.SleepTimer
import kotlin.time.Duration
import voice.app.TestApp // Added
// import voice.app.fakes.FakePlayerController // Removed
// import voice.app.fakes.FakePlayStateManager // Removed
import voice.app.fakes.FakeShakeDetector // Added for injection
import voice.app.injection.DaggerTestAppComponent // Ensure this is imported
import voice.app.injection.TestAppComponent // Added
import voice.common.pref.SleepTimeStore // Added
import voice.playback.session.PlaybackService // Added
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

    @get:Rule
    val serviceRule = ServiceTestRule() // Added

    private lateinit var context: Context
    private lateinit var testAppComponent: TestAppComponent
    // private lateinit var fakeShakeDetector: FakeShakeDetector // Removed for now, SleepTimer gets real one
    private lateinit var exoPlayer: ExoPlayer // Test ExoPlayer
    // private lateinit var fakeMediaSource: FakeMediaSource // Removed for now

    @Inject // Real SleepTimer will be injected
    lateinit var sleepTimer: SleepTimer
    
    @Inject // Real PlayerController
    lateinit var playerController: PlayerController

    @Inject // Real PlayStateManager
    lateinit var playStateManager: PlayStateManager

    @Inject
    @SleepTimeStore
    lateinit var sleepTimeStore: DataStore<Int>

    @Inject // Added for Part 1d
    lateinit var fakeShakeDetector: FakeShakeDetector 
    
    // FakeBookRepository and FakeMediaItemProvider are not directly used by the test,
    // but are injected into PlayerController by Dagger.

    // Inner FakeShakeDetector class removed. It's now a top-level class in voice.app.fakes
    // and provided by Dagger.

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // 1. Create the test ExoPlayer instance FIRST
        exoPlayer = ExoPlayer.Builder(context)
            .setLooper(mainCoroutineRule.testDispatcher.scheduler.looper)
            .build()

        // 2. Create and set TestAppComponent, passing the ExoPlayer instance
        testAppComponent = DaggerTestAppComponent.factory().create(
            application = context.applicationContext as Application,
            player = exoPlayer // Pass our test ExoPlayer to Dagger
        )
        // Set the global component for the TestApp and any other Dagger users
        voice.app.injection.appComponent = testAppComponent
        voice.common.rootComponent = testAppComponent
        
        // 3. Inject dependencies into this test class
        testAppComponent.inject(this) // Injects sleepTimer, playerController, playStateManager, sleepTimeStore

        // 4. Start PlaybackService
        val serviceIntent = Intent(context, PlaybackService::class.java)
        try {
            serviceRule.startService(serviceIntent)
        } catch (e: Exception) {
            throw RuntimeException("Failed to start PlaybackService", e)
        }

        // 5. Initialize DataStore default value
        mainCoroutineRule.testDispatcher.scheduler.runCurrent()
        runBlocking(mainCoroutineRule.testDispatcher) {
            sleepTimeStore.updateData { 20.minutes.inWholeMinutes.toInt() }
        }
        
        // 6. FakeShakeDetector is no longer instantiated here for direct use.
        // If shake tests are needed, FakeShakeDetector should be provided via Dagger
        // by updating TestPrefsModule to bind ShakeDetector to FakeShakeDetector.
        // For now, the real ShakeDetector will be used by the injected SleepTimer.
    }

    @After
    fun tearDown() {
        // ServiceTestRule handles service cleanup.
        
        exoPlayer.release() // Release our ExoPlayer instance

        runBlocking(mainCoroutineRule.testDispatcher) {
            sleepTimeStore.updateData { 20.minutes.inWholeMinutes.toInt() }
        }
        mainCoroutineRule.testDispatcher.scheduler.advanceUntilIdle() // Ensure all coroutines are done
        mainCoroutineRule.testDispatcher.scheduler.cancel()
    }

    // Test methods will be updated later to use injected real components
    // The existing tests (basicTest, testSleepTimer_startsAndPausesPlayback, 
    // testSleepTimer_canBeCancelled, testSleepTimer_shakeResetsTimer)
    // will likely FAIL or behave differently with real components and no FakePlayerController/Manager.
    // The shake test specifically will use a real ShakeDetector now.
    @Test
    fun basicTest() { 
        assert(true)
    }

    @Test
    fun testSleepTimer_startsAndPausesPlayback() = mainCoroutineRule.testDispatcher.runTest {
        val testSleepDuration = 15.seconds 
        
        val fadeOutDurationField = SleepTimer::class.java.getDeclaredField("fadeOutDuration")
        fadeOutDurationField.isAccessible = true
        val fadeOutDuration = fadeOutDurationField.get(sleepTimer) as Duration

        // Initial state is set in @Before (player is playing)
        assertTrue(playStateManager.playState == PlayStateManager.PlayState.Playing, "Player should be playing at start")
        assertEquals(1.0f, exoPlayer.volume, "Initial volume should be 1.0f")

        sleepTimer.setActive(testSleepDuration)
        assertEquals(testSleepDuration, sleepTimer.leftSleepTimeFlow.value, "Timer should start with test duration")
        assertTrue(sleepTimer.sleepTimerActive(), "Sleep timer should be active after starting")

        val timeBeforeFadeStart = testSleepDuration - fadeOutDuration - 1.seconds
        if (timeBeforeFadeStart > Duration.ZERO) {
            advanceTimeBy(timeBeforeFadeStart.inWholeMilliseconds)
            runCurrent() 
            assertEquals(testSleepDuration - timeBeforeFadeStart, sleepTimer.leftSleepTimeFlow.value, "Time left should be correct before fade")
            assertTrue(playStateManager.playState == PlayStateManager.PlayState.Playing, "Player should still be playing before fade")
            assertEquals(1.0f, exoPlayer.volume, "Volume should be at max before fade out")
        }

        var lastVolume = exoPlayer.volume
        var advancedDuringFade = 0L
        val totalFadeMillis = fadeOutDuration.inWholeMilliseconds
        val checkIntervalMillis = 200L // Reduced interval for more granular check

        while(advancedDuringFade < totalFadeMillis && sleepTimer.leftSleepTimeFlow.value > Duration.ZERO) {
            val timeToAdvanceThisStep = kotlin.math.min(checkIntervalMillis, totalFadeMillis - advancedDuringFade)
            if (timeToAdvanceThisStep <= 0) break 

            advanceTimeBy(timeToAdvanceThisStep)
            runCurrent()
            advancedDuringFade += timeToAdvanceThisStep

            val currentVolume = exoPlayer.volume
            val timeLeft = sleepTimer.leftSleepTimeFlow.value

            if (timeLeft < fadeOutDuration && timeLeft > Duration.ZERO) { 
                assertTrue(currentVolume < lastVolume || currentVolume == 0f,
                    "Volume should be decreasing or 0f during fade. Current: $currentVolume, Last: $lastVolume, TimeLeft: $timeLeft")
            }
            lastVolume = currentVolume
        }
        
        val totalTimeAdvancedSoFar = timeBeforeFadeStart.coerceAtLeast(Duration.ZERO).inWholeMilliseconds + advancedDuringFade
        val remainingTimeForSleepDuration = testSleepDuration.inWholeMilliseconds - totalTimeAdvancedSoFar
        if (remainingTimeForSleepDuration > 0) {
            advanceTimeBy(remainingTimeForSleepDuration)
            runCurrent()
        }

        advanceTimeBy(500) // Buffer for final actions
        runCurrent()

        assertEquals(Duration.ZERO, sleepTimer.leftSleepTimeFlow.value, "Timer should be at zero after completion")
        assertTrue(playStateManager.playState == PlayStateManager.PlayState.Paused, "Player should be paused after timer ends")
        assertEquals(1.0f, exoPlayer.volume, "Volume should be reset to 1.0f after timer completion")
        assertFalse(sleepTimer.sleepTimerActive(), "Sleep timer should be inactive after completion")
    }

    @Test
    fun testSleepTimer_canBeCancelled() = mainCoroutineRule.runTest {
        val testSleepDuration = 20.seconds 

        // Initial state set in @Before
        assertTrue(playStateManager.playState == PlayStateManager.PlayState.Playing, "Player should be playing at start")
        assertEquals(1.0f, exoPlayer.volume, "Initial volume should be 1.0f")

        sleepTimer.setActive(testSleepDuration)
        assertEquals(testSleepDuration, sleepTimer.leftSleepTimeFlow.value, "Timer should start with test duration")
        assertTrue(sleepTimer.sleepTimerActive(), "Sleep timer should be active after starting")

        val advanceDuration = 5.seconds
        advanceTimeBy(advanceDuration.inWholeMilliseconds)
        runCurrent() 

        assertEquals(testSleepDuration - advanceDuration, sleepTimer.leftSleepTimeFlow.value, "Time left should have decreased")
        assertTrue(sleepTimer.sleepTimerActive(), "Sleep timer should still be active before cancellation")
        assertTrue(playStateManager.playState == PlayStateManager.PlayState.Playing, "Player should still be playing before cancellation")

        sleepTimer.setActive(false) // Cancel the timer
        runCurrent() 

        assertEquals(Duration.ZERO, sleepTimer.leftSleepTimeFlow.value, "Timer should be at zero after cancellation")
        assertFalse(sleepTimer.sleepTimerActive(), "Sleep timer should be inactive after cancellation")
        
        assertTrue(playStateManager.playState == PlayStateManager.PlayState.Playing, "Player should still be playing after timer cancellation")
        assertEquals(1.0f, exoPlayer.volume, "Volume should be reset to 1.0f after cancellation")

        advanceTimeBy(testSleepDuration.inWholeMilliseconds) // Advance well past original end time
        runCurrent()

        assertTrue(playStateManager.playState == PlayStateManager.PlayState.Playing, "Player should remain playing after cancellation and further time")
        assertEquals(Duration.ZERO, sleepTimer.leftSleepTimeFlow.value, "Timer should remain at zero after cancellation")
        assertFalse(sleepTimer.sleepTimerActive(), "Sleep timer should remain inactive after cancellation")
    }

    @Test
    fun testSleepTimer_shakeResetsTimer() = mainCoroutineRule.runTest {
        val initialSleepDuration = 15.seconds 

        // Initial state set in @Before
        assertTrue(playStateManager.playState == PlayStateManager.PlayState.Playing, "Player should be playing at start")
        assertEquals(1.0f, exoPlayer.volume, "Initial volume should be 1.0f")

        sleepTimer.setActive(initialSleepDuration)
        assertEquals(initialSleepDuration, sleepTimer.leftSleepTimeFlow.value, "Timer should start with initial duration")
        assertTrue(sleepTimer.sleepTimerActive(), "Sleep timer should be active after starting")

        val timeToAdvance = 5.seconds
        assertTrue(timeToAdvance < initialSleepDuration, "Time advanced should be less than sleep duration for this test logic")

        advanceTimeBy(timeToAdvance.inWholeMilliseconds) // Corrected 'advanceTimeby' to 'advanceTimeBy'
        runCurrent() 

        assertEquals(initialSleepDuration - timeToAdvance, sleepTimer.leftSleepTimeFlow.value, "Timer should have counted down before shake")

        fakeShakeDetector.emitShake() // Use the injected fake
        runCurrent() 

        // Assertions after shake: SleepTimer calls playerController.play() and resets volume to 1F.
        assertEquals(initialSleepDuration, sleepTimer.leftSleepTimeFlow.value, "Timer should reset to initial duration after shake")
        assertTrue(sleepTimer.sleepTimerActive(), "Sleep timer should remain active after shake reset")
        assertTrue(playStateManager.playState == PlayStateManager.PlayState.Playing, "Player should be playing after shake reset")
        assertEquals(1.0f, exoPlayer.volume, "Volume should be 1.0f after shake reset")

        // Let the timer run down completely
        advanceTimeBy(initialSleepDuration.inWholeMilliseconds)
        runCurrent()
        advanceTimeBy(1000) // Buffer for any final processing
        runCurrent()

        assertEquals(Duration.ZERO, sleepTimer.leftSleepTimeFlow.value, "Timer should eventually reach zero after reset")
        assertTrue(playStateManager.playState == PlayStateManager.PlayState.Paused, "Player should be paused after timer completes following a reset")
        assertFalse(sleepTimer.sleepTimerActive(), "Sleep timer should be inactive after completing post-reset")
    }
}
