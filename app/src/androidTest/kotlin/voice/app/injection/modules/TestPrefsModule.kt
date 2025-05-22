package voice.app.injection.modules

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import voice.app.fakes.FakePlayStateManager
import voice.app.fakes.FakePlayerController
// SleepTimerTest.FakeShakeDetector is not provided here as SleepTimer will be manually constructed in tests
import voice.common.pref.SleepTimeStore
import voice.playback.PlayerController // Only used in the REMOVED binding
import voice.playback.playstate.PlayStateManager
// import voice.sleepTimer.ShakeDetector // REMOVED provider
import javax.inject.Singleton

@Module
object TestPrefsModule {

    // --- DataStore Providers ---

    @Provides
    @Singleton
    fun provideTestPreferencesDataStore(context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("test_sleep_timer_module_prefs") }
        )
    }

    @Provides
    @Singleton
    @SleepTimeStore
    fun provideSleepTimeDataStore(
        prefsDataStore: DataStore<Preferences>
    ): DataStore<Int> {
        val sleepTimeKey = intPreferencesKey("sleep_time_duration_minutes")
        
        return object : DataStore<Int> {
            override val data = prefsDataStore.data.map { prefs ->
                prefs[sleepTimeKey] ?: 20 // Default value
            }

            override suspend fun updateData(transform: suspend (t: Int) -> Int): Int {
                val newPrefs = prefsDataStore.edit { prefs ->
                    val currentValue = prefs[sleepTimeKey] ?: 20
                    val newValue = transform(currentValue)
                    prefs[sleepTimeKey] = newValue
                }
                return newPrefs[sleepTimeKey] ?: 20
            }
        }
    }

    // --- Fake Object Providers ---

    @Provides
    @Singleton
    fun provideFakePlayStateManager(): FakePlayStateManager {
        return FakePlayStateManager()
    }

    @Provides
    @Singleton
    fun bindPlayStateManager(fake: FakePlayStateManager): PlayStateManager {
        // This is valid because FakePlayStateManager now extends PlayStateManager (as per previous step)
        return fake
    }

    @Provides
    @Singleton
    fun provideFakePlayerController(): FakePlayerController {
        return FakePlayerController()
    }

    // The binding for PlayerController (bindPlayerController) has been REMOVED as instructed.
    // The provider for ShakeDetector (provideFakeShakeDetector) has been REMOVED as instructed.
}
