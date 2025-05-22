package voice.app.injection.modules

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey // Added
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import dagger.Binds // Added
import voice.app.fakes.FakeBookRepository // Added
import voice.app.fakes.FakeMediaItemProvider // Added
import voice.app.fakes.FakeShakeDetector // Added
// SleepTimerTest.FakeShakeDetector is not provided here as SleepTimer will be manually constructed in tests
import voice.common.pref.SleepTimeStore
import voice.common.pref.CurrentBookStore // Added
import voice.common.BookId // Added
// import voice.playback.PlayerController // No longer needed
// import voice.playback.playstate.PlayStateManager // No longer needed for fake binding
import voice.data.repo.BookRepository // Added
import voice.playback.session.MediaItemProvider // Added
import voice.sleepTimer.ShakeDetector // Added
import javax.inject.Singleton
// import kotlinx.serialization.builtins.nullable // Not used in provided snippet
// import voice.common.serialization.BookIdSerializer // Not used in provided snippet


@Module
abstract class TestPrefsModule { // Changed to abstract class for @Binds

    // --- Fake Bindings ---
    @Binds
    @Singleton
    abstract fun bindBookRepository(fake: FakeBookRepository): BookRepository

    @Binds
    @Singleton
    abstract fun bindMediaItemProvider(fake: FakeMediaItemProvider): MediaItemProvider

    @Binds
    @Singleton // Match scope of real ShakeDetector if it has one
    abstract fun bindShakeDetector(fake: FakeShakeDetector): ShakeDetector

    // Companion object for @Provides methods (static)
    companion object {
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
                    prefs[sleepTimeKey] ?: 20
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

        @Provides
        @Singleton
        @CurrentBookStore
        fun provideCurrentBookStore(
            prefsDataStore: DataStore<Preferences> // Use the same test DataStore<Preferences>
        ): DataStore<BookId?> {
            val currentBookKey = stringPreferencesKey("current_book_id_test")
            return object : DataStore<BookId?> {
                override val data = prefsDataStore.data.map { prefs ->
                    prefs[currentBookKey]?.let { BookId(it) }
                }

                override suspend fun updateData(transform: suspend (t: BookId?) -> BookId?): BookId? {
                    val newPrefs = prefsDataStore.edit { prefs ->
                        val currentIdString = prefs[currentBookKey]
                        val currentBookId = currentIdString?.let { BookId(it) }
                        val newBookId = transform(currentBookId)
                        if (newBookId == null) {
                            prefs.remove(currentBookKey)
                        } else {
                            prefs[currentBookKey] = newBookId.value
                        }
                    }
                    return newPrefs[currentBookKey]?.let { BookId(it) }
                }
            }
        }
    }
    // Removed providers for FakePlayerController, FakePlayStateManager, and bindPlayStateManager
}
