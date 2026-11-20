@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.hopla.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hopla.data.AuthRepository
import app.hopla.ui.components.Headline
import app.hopla.ui.components.HoplaShapes
import app.hopla.ui.components.MorphingBlob
import app.hopla.ui.components.RopeBackdrop
import kotlinx.coroutines.launch

/** Écran d'attente pendant que Supabase restaure la session. */
@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RopeBackdrop(Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Headline("Hopla", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            LoadingIndicator(Modifier.size(64.dp))
        }
    }
}

@Composable
private fun AuthScaffold(title: String, subtitle: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        RopeBackdrop(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            MorphingBlob(HoplaShapes.hero, MaterialTheme.colorScheme.primary, Modifier.size(150.dp), cycleMillis = 2200) {
                Headline("Hop", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
            }
            Spacer(Modifier.height(24.dp))
            Headline(title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BigAction(text: String, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.LargeContainerHeight),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight),
    ) {
        AnimatedContent(loading, label = "btn") { busy ->
            if (busy) ContainedLoadingIndicator(Modifier.size(36.dp))
            else Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Connexion sans mot de passe : e-mail → code à 6 chiffres. */
@Composable
fun SignInScreen(auth: AuthRepository) {
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var codeSent by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val emailValid = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())

    fun send() {
        loading = true; error = null
        scope.launch {
            auth.sendCode(email).onSuccess { codeSent = true }.onFailure { error = it.message }
            loading = false
        }
    }

    AnimatedContent(
        targetState = codeSent,
        transitionSpec = { (slideInHorizontally { it / 2 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 2 } + fadeOut()) },
        label = "auth",
    ) { sent ->
        if (!sent) {
            AuthScaffold("Rejoins la bande", "Entre ton e-mail, on t'envoie un code. Pas de mot de passe à retenir.") {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; error = null },
                    label = { Text("E-mail") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                BigAction("Recevoir mon code", loading, emailValid) { send() }
            }
        } else {
            AuthScaffold("Ton code", "Envoyé à ${email.trim()}. Il arrive en quelques secondes (pense aux spams).") {
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.filter(Char::isDigit).take(8); error = null },
                    label = { Text("Code") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg) } },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 8.sp, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                BigAction("C'est moi !", loading, code.length >= 6) {
                    loading = true; error = null
                    scope.launch {
                        // En cas de succès, la session change et l'app passe d'elle-même à l'écran suivant.
                        auth.verifyCode(email, code).onFailure { error = it.message }
                        loading = false
                    }
                }
                TextButton(onClick = { send() }, enabled = !loading) { Text("Renvoyer le code") }
                TextButton(onClick = { codeSent = false; code = "" }) { Text("Changer d'e-mail") }
            }
        }
    }
}

/** Première connexion : choisir son nom et son pseudo public. */
@Composable
fun CreateProfileScreen(auth: AuthRepository, email: String?) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }?.take(24) ?: "") }
    var handle by rememberSaveable {
        mutableStateOf(email?.substringBefore("@")?.lowercase()?.filter { it.isLetterOrDigit() || it in "._" }?.take(20) ?: "")
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = name.isNotBlank() && Regex("^[a-z0-9._]{3,20}$").matches(handle)

    AuthScaffold("Ton blaze", "C'est comme ça que ta bande te trouvera. Tu pourras le changer plus tard.") {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(24) },
            label = { Text("Prénom") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = handle,
            onValueChange = { v -> handle = v.lowercase().filter { it.isLetterOrDigit() || it in "._" }.take(20); error = null },
            label = { Text("Pseudo") },
            prefix = { Text("@") },
            singleLine = true,
            isError = error != null,
            supportingText = { Text(error ?: "3 à 20 caractères : lettres, chiffres, . et _") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        BigAction("On saute !", loading, valid) {
            loading = true; error = null
            scope.launch {
                auth.createProfile(name, handle).onFailure { error = it.message }
                loading = false
            }
        }
        TextButton(onClick = { scope.launch { auth.signOut() } }) { Text("Utiliser un autre e-mail") }
    }
}
