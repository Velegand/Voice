package voice.app.fakes

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import voice.sleepTimer.ShakeDetector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Or appropriate scope matching real ShakeDetector if it has one
class FakeShakeDetector @Inject constructor(
    // Real ShakeDetector takes: @Inject constructor(private val context: Context, @MinShakeThreshold private val threshold: Float, @ShakeCoolDownMs private val coolDownTimeMs: Int)
    // We need to provide these or ensure they have qualifiers that can be satisfied by test Dagger.
    // For a simple fake, these might not be strictly needed if not used.
    // However, to be a valid substitute for Dagger, the constructor needs to be satisfiable.
    // Let's assume Dagger can provide Context, and we provide dummy values for threshold/cooldown via Test Dagger if needed.
    // For now, let's make its constructor simple for the fake's purpose if Dagger allows.
    // The real ShakeDetector is @Singleton.
    // If ShakeDetector's dependencies (@MinShakeThreshold Int, @ShakeCoolDownMs Int) are not in the graph, this will fail.
    // Let's assume for now that they are or we add dummy providers for them.
    // The simplest is to match the constructor of the real one if possible.
    // The real one gets context from AndroidModule. Threshold and Cooldown are from another module (not yet included).
    // For now, let FakeShakeDetector have a simple constructor and we bind IT.
    // This is only possible if FakeShakeDetector *IS-A* ShakeDetector.
    context: Context // Dagger will provide this from AndroidModule
) : ShakeDetector(context, 20f, 500) { // Call super with some default values
    private val flow = MutableSharedFlow<Unit>(replay = 1)
    suspend fun emitShake() { flow.tryEmit(Unit) }
    override suspend fun detect() { flow.first() }
    override fun start(threshold: Float, coolDownTimeMs: Int) { /* No-op for fake */ }
    override fun stop() { /* No-op for fake */ }
}
