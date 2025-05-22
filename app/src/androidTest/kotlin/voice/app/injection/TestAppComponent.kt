package voice.app.injection

import android.app.Application
// import android.content.Context // Not directly used in this file, but AndroidModule provides it.
import dagger.BindsInstance
import dagger.Component
import voice.app.fakes.FakePlayerController
import voice.app.fakes.FakePlayStateManager
import voice.app.injection.modules.AndroidModule // Assuming this path is correct
import voice.app.injection.modules.TestPrefsModule
import voice.app.sleepTimer.SleepTimerTest
// import voice.common.AppScope // AppScope is not a Dagger scope, usually @Singleton or custom scope is used.
                                // AppComponent itself is likely @Singleton or a custom scope.
import javax.inject.Singleton

@Singleton // Matches the scope of the component it replaces (AppComponent)
@Component(
    modules = [
        TestPrefsModule::class,
        AndroidModule::class // Provides Context, Dispatchers, etc.
    ]
)
interface TestAppComponent : AppComponent { // Extends the real AppComponent

    // Injection target for the test class itself.
    fun inject(target: SleepTimerTest)

    // Factory to create instances of TestAppComponent
    @Component.Factory
    interface Factory {
        fun create(@BindsInstance application: Application): TestAppComponent
    }

    // Expose FakePlayerController directly so SleepTimerTest can get it
    fun getFakePlayerController(): FakePlayerController
    
    // Expose FakePlayStateManager directly for test control
    fun getFakePlayStateManager(): FakePlayStateManager

    // Companion object to easily create the component
    companion object {
        // This relies on Dagger generating DaggerTestAppComponent
        fun factory(): Factory = DaggerTestAppComponent.factory()
    }
}
