@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.hopla.ui.profile

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.hopla.data.DarkMode
import app.hopla.data.PaletteId
import app.hopla.data.Sensitivity
import app.hopla.data.TitleStyleId
import app.hopla.data.UserSettings
import app.hopla.ui.components.ConnectedChoice
import app.hopla.ui.components.ExpressiveLoading
import app.hopla.ui.components.Headline
import app.hopla.ui.components.Stepper
import app.hopla.ui.theme.Palettes
import app.hopla.ui.theme.hoplaTypography
import app.hopla.ui.theme.titleTheme

@Composable
fun ProfileScreen(
    settings: UserSettings?,
    isRemote: Boolean,
    profileError: String?,
    onUpdate: ((UserSettings) -> UserSettings) -> Unit,
    onIdentity: (name: String, handle: String) -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Headline("Toi", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        if (settings == null) {
            ExpressiveLoading("Chargement…")
            return@Column
        }

        Card("Profil") {
            // État local pour une saisie fluide (DataStore est asynchrone)
            var name by rememberSaveable { mutableStateOf(settings.name) }
            var handle by rememberSaveable { mutableStateOf(settings.handle) }
            OutlinedTextField(
                value = name,
                onValueChange = { v ->
                    name = v.take(24)
                    onIdentity(name, handle)
                },
                label = { Text("Prénom") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = handle,
                onValueChange = { v ->
                    handle = v.lowercase().filter { c -> c.isLetterOrDigit() || c in "._" }.take(20)
                    onIdentity(name, handle)
                },
                label = { Text("Pseudo") },
                prefix = { Text("@") },
                singleLine = true,
                isError = profileError != null,
                supportingText = profileError?.let { msg -> { Text(msg) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Stepper("Poids", "${settings.weightKg} kg",
                onMinus = { onUpdate { it.copy(weightKg = (it.weightKg - 1).coerceAtLeast(30)) } },
                onPlus = { onUpdate { it.copy(weightKg = (it.weightKg + 1).coerceAtMost(200)) } })
            Text(
                "Le poids sert uniquement au calcul des calories (méthode MET).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Stepper("Objectif / jour", "${settings.dailyGoal}",
                onMinus = { onUpdate { it.copy(dailyGoal = (it.dailyGoal - 100).coerceAtLeast(100)) } },
                onPlus = { onUpdate { it.copy(dailyGoal = (it.dailyGoal + 100).coerceAtMost(20_000)) } })
        }

        Card("Détection") {
            ConnectedChoice(Sensitivity.entries, settings.sensitivity, { s -> onUpdate { it.copy(sensitivity = s) } }, { it.label })
            Text(
                "« Vive » capte les petits sauts (téléphone en main), « Douce » évite les faux positifs (poche ample).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card("Couleurs") {
            PalettePicker(settings.palette) { p -> onUpdate { it.copy(palette = p) } }
            ConnectedChoice(DarkMode.entries, settings.darkMode, { m -> onUpdate { it.copy(darkMode = m) } }, { it.label })
        }

        Card("Titres") {
            TitleStyleId.entries.forEach { id ->
                TitleStyleOption(id, selected = id == settings.titleStyle) { onUpdate { it.copy(titleStyle = id) } }
            }
        }

        Card("Compte") {
            if (isRemote) {
                Text(
                    "Tes séances sont synchronisées avec ta bande. Hors ligne, elles attendent sur le téléphone et partent dès que le réseau revient.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onSignOut, shapes = ButtonDefaults.shapes()) { Text("Se déconnecter") }
            } else {
                Text(
                    "Mode démo : aucune clé Supabase n'est configurée, les amis sont simulés. Ajoute supabase.url et supabase.key dans local.properties pour activer le vrai partage.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Card("Comment ça marche ?") {
            Text(
                "Hopla écoute l'accéléromètre ~100 fois par seconde. Chaque réception de saut crée un pic " +
                    "d'accélération : un seuil qui s'adapte à ton intensité les repère, en ignorant ce qui " +
                    "arrive trop vite pour être un vrai saut. Les calories suivent ton rythme (MET 8,8 à 12,3). " +
                    "Écran verrouillé, un service garde le capteur actif et la notification affiche le compteur.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Headline(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun PalettePicker(selected: PaletteId, onSelect: (PaletteId) -> Unit) {
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        PaletteId.entries.filter { it != PaletteId.DYNAMIC || dynamicAvailable }.forEach { id ->
            val isSelected = id == selected
            // La pastille sélectionnée passe d'un cercle à un "cookie" qui tourne
            val shape = if (isSelected) MaterialShapes.Cookie9Sided.toShape() else MaterialShapes.Circle.toShape()
            val rotation by animateFloatAsState(if (isSelected) 45f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "rot")
            val scale by animateFloatAsState(if (isSelected) 1.1f else 0.9f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "scale")
            val color = if (id == PaletteId.DYNAMIC) MaterialTheme.colorScheme.secondary else Palettes.getValue(id).accent
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(52.dp)
                        .graphicsLayer {
                            rotationZ = rotation
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(shape)
                        .background(color)
                        .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier)
                        .clickable { onSelect(id) },
                )
                Spacer(Modifier.height(4.dp))
                Text(id.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun TitleStyleOption(id: TitleStyleId, selected: Boolean, onClick: () -> Unit) {
    val style = remember(id) { hoplaTypography(titleTheme(id)).headlineLarge }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (id.uppercase) "HOP LÀ !" else "Hop là !", style = style, modifier = Modifier.weight(1f), maxLines = 1)
            Text(id.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
