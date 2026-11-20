package app.hopla

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationManagerCompat
import app.hopla.workout.JumpTrackingService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hopla.data.UserSettings
import app.hopla.ui.nav.HoplaRoot
import app.hopla.ui.theme.HoplaTheme

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        // Le bilan de séance est visible dans l'app : on retire la notification correspondante.
        NotificationManagerCompat.from(this).cancel(JumpTrackingService.SUMMARY_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HoplaApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
            HoplaTheme(
                palette = settings.palette,
                titleStyle = settings.titleStyle,
                darkMode = settings.darkMode,
            ) {
                HoplaRoot(container)
            }
        }
    }
}
