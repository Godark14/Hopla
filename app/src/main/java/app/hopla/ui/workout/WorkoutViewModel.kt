package app.hopla.ui.workout

import androidx.lifecycle.ViewModel
import app.hopla.AppContainer
import app.hopla.workout.WorkoutMode
import app.hopla.workout.WorkoutUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * Façade UI du [app.hopla.workout.WorkoutEngine]. Le moteur vit dans le processus
 * (et dans le service de premier plan) : quitter l'écran ou l'éteindre n'arrête pas la séance.
 */
class WorkoutViewModel(container: AppContainer) : ViewModel() {
    private val engine = container.engine
    val state: StateFlow<WorkoutUiState> = engine.state

    fun prepare(mode: WorkoutMode) = engine.prepare(mode)
    fun setMode(mode: WorkoutMode) = engine.setMode(mode)
    fun start() = engine.start()
    fun togglePause() = engine.togglePause()
    fun finish() = engine.finish()
    fun save(onDone: () -> Unit) = engine.save(onDone)
    fun reset() = engine.reset()
}
