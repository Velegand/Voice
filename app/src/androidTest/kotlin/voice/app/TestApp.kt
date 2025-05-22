package voice.app

import android.util.Log
import voice.app.injection.App
import voice.app.injection.DaggerTestAppComponent // Import generated Dagger component
// import voice.app.injection.TestAppComponent // Not directly used, DaggerTestAppComponent is the instance
import voice.app.injection.appComponent // static import for assignment
import voice.common.rootComponent // static import for assignment

class TestApp : App() {
    override fun onCreate() {
        super.onCreate() // Initializes production appComponent, very important this is called first.

        Log.d("TestApp", "TestApp onCreate: Initializing and setting Test Dagger Component.")
        val testAppComponent = DaggerTestAppComponent.factory().create(this)
        
        // Replace the globally accessible component instances.
        // These assume 'appComponent' and 'rootComponent' are settable static/companion properties.
        appComponent = testAppComponent 
        // voice.app.injection.appComponent = testAppComponent // Explicit qualification if simple assignment doesn't work
        rootComponent = testAppComponent
        // voice.common.rootComponent = testAppComponent // Explicit qualification if simple assignment doesn't work
        
        // If TestApp itself needed injection (it doesn't in this example):
        // testAppComponent.inject(this)
    }
}
