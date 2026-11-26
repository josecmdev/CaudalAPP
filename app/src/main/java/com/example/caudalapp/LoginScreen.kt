package com.example.caudalapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.caudalapp.sync.CaudalSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pantalla al abrir la app cuando Supabase está configurado y no hay sesión. Nunca bloquea: hay modo prueba. */
@Composable
fun LoginScreen(onUseWithoutAccount: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Caudal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Inicia sesión con el usuario que te dio el administrador.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    user,
                    { user = it },
                    label = { Text("Usuario o correo") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            error = withContext(Dispatchers.IO) { CaudalSync.signIn(context, user, password) }
                                .exceptionOrNull()?.let(CaudalSync::loginErrorMessage)
                            busy = false
                        }
                    },
                    enabled = !busy && user.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Entrando…" else "Entrar") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = onUseWithoutAccount, modifier = Modifier.fillMaxWidth()) {
                    Text("Usar sin cuenta (modo prueba)")
                }
                Text(
                    "Sin cuenta todo funciona igual en esta tablet, solo que no se envía nada a la oficina. " +
                        "Puedes entrar después en Ajustes → Cuenta y sincronización.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
