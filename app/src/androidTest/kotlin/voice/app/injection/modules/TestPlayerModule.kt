package voice.app.injection.modules

import androidx.media3.common.Player
import dagger.Module
import dagger.Provides
import voice.app.injection.TestAppComponent // To access the ExoPlayer instance
import javax.inject.Singleton

@Module
object TestPlayerModule {
    @Provides
    @Singleton
    fun providePlayer(testAppComponent: TestAppComponent): Player {
        // This assumes TestAppComponent has a way to get the ExoPlayer instance
        // that was created in SleepTimerTest and passed to TestAppComponent's factory.
        return testAppComponent.getExoPlayerInstance()
    }
}
