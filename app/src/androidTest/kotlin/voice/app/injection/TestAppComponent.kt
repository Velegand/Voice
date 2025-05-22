package voice.app.injection

import android.app.Application
// import android.content.Context // Not directly used in this file, but AndroidModule provides it.
import androidx.media3.common.Player // Import Player
import dagger.BindsInstance // Import BindsInstance
import dagger.Component
import voice.app.injection.modules.AndroidModule // Added back as per new instructions
import voice.app.injection.modules.TestPrefsModule
import voice.app.injection.modules.TestPlayerModule // Added
import voice.app.sleepTimer.SleepTimerTest
import voice.playback.di.PlaybackModule // Added (real playback module)
// import voice.common.AppScope // AppScope is not a Dagger scope, usually @Singleton or custom scope is used.
                                // AppComponent itself is likely @Singleton or a custom scope.
import javax.inject.Singleton

@Singleton // Matches the scope of the component it replaces (AppComponent)
@Component(
    modules = [
        TestPrefsModule::class,
        TestPlayerModule::class, // Provides our test ExoPlayer as Player
        PlaybackModule::class,   // Provides other playback related dependencies
        AndroidModule::class     // Provides Context, Dispatchers etc.
    ]
)
interface TestAppComponent : AppComponent { // Still extends real AppComponent for other injections

    fun inject(target: SleepTimerTest)
    // fun inject(target: TestApp) // If needed

    // This method is how TestPlayerModule will get the ExoPlayer instance.
    // The instance itself will be passed in during component creation.
    fun getExoPlayerInstance(): Player

    @Component.Factory
    interface Factory {
        // Application is from AppComponent.
        // We add @BindsInstance for Player here.
        fun create(
            @BindsInstance application: Application,
            @BindsInstance player: Player // The test ExoPlayer instance
        ): TestAppComponent
    }

    companion object {
        // Modified factory() method to show it needs the Player instance
        // The actual call will be DaggerTestAppComponent.factory().create(app, player)
        // This is just a conceptual guide. The generated Dagger factory will have this signature.
        // Actual factory access: DaggerTestAppComponent.factory()
    }
}
