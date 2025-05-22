package voice.app

import android.util.Log
import voice.app.injection.App
// import voice.app.injection.DaggerTestAppComponent // No longer initialized here
// import voice.app.injection.TestAppComponent
// import voice.app.injection.appComponent // No longer set here
// import voice.common.rootComponent // No longer set here

class TestApp : App() {
    override fun onCreate() {
        super.onCreate() // Initializes production appComponent
        Log.d("TestApp", "TestApp onCreate: Production Dagger component initialized by super.onCreate().")
        // SleepTimerTest will be responsible for creating and setting its own TestAppComponent
        // with the test ExoPlayer instance.
        // Global appComponent and rootComponent will remain as the production ones unless
        // a specific test (like SleepTimerTest) overrides them for its scope.
    }
}
