package com.bakhawone.thesis_bakhawone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakhawone.thesis_bakhawone.ui.theme.ThesisbakhawoneTheme
import com.google.firebase.auth.FirebaseAuth
import java.util.regex.Pattern

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        val isRemembered = prefs.getBoolean("remember_me", false)
        val currentUser = FirebaseAuth.getInstance().currentUser
        
        if (isRemembered && currentUser != null) {
            val intent = Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
            return
        }

        setContent {
            ThesisbakhawoneTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LoginScreen(
                        onLoginSuccess = {
                            val intent = Intent(this, DashboardActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var resetErrorText by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }

    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current

    fun isValidEmail(email: String): Boolean {
        val emailPattern = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"
        )
        return emailPattern.matcher(email.trim()).matches()
    }

    fun validateLoginInputs(): String? {
        return when {
            email.isBlank() || password.isBlank() -> "Please enter email and password"
            !isValidEmail(email) -> "Please enter a valid email address"
            else -> null
        }
    }

    fun handleLogin() {
        val validationError = validateLoginInputs()
        if (validationError != null) {
            errorText = validationError
            return
        }

        isLoading = true
        errorText = null
        
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                isLoading = false
                if (task.isSuccessful) {
                    // Persist remember me preference
                    val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("remember_me", rememberMe).apply()
                    onLoginSuccess()
                } else {
                    errorText = task.exception?.localizedMessage ?: "Login failed"
                }
            }
    }

    fun handlePasswordReset() {
        resetErrorText = null
        
        when {
            resetEmail.isBlank() -> {
                resetErrorText = "Please enter your email"
                return
            }
            !isValidEmail(resetEmail) -> {
                resetErrorText = "Please enter a valid email address"
                return
            }
        }

        auth.sendPasswordResetEmail(resetEmail.trim())
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showResetDialog = false
                    errorText = "Password reset email sent. Please check your inbox."
                    resetEmail = ""
                } else {
                    resetErrorText = task.exception?.localizedMessage ?: "Failed to send reset email"
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.bakhawone),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Sign in",
                style = MaterialTheme.typography.headlineSmall,
                fontSize = 22.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { 
                    email = it
                    errorText = null
                },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { 
                    password = it
                    errorText = null
                },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) 
                    VisualTransformation.None 
                else 
                    PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                trailingIcon = {
                    val visibilityIcon = if (passwordVisible) 
                        Icons.Default.VisibilityOff 
                    else 
                        Icons.Default.Visibility
                    val description = if (passwordVisible) 
                        "Hide password" 
                    else 
                        "Show password"
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = visibilityIcon, 
                            contentDescription = description
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = rememberMe, 
                    onCheckedChange = { rememberMe = it }
                )
                Text(text = "Remember me")
            }

            if (errorText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorText!!, 
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { handleLogin() },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(text = "Login")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = {
                        resetEmail = email
                        resetErrorText = null
                        showResetDialog = true
                    }
                ) {
                    Text("Forgot password?")
                }

                TextButton(
                    onClick = {
                        context.startActivity(Intent(context, SignupActivity::class.java))
                    }
                ) {
                    Text("Create account")
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { 
                showResetDialog = false
                resetErrorText = null
            },
            title = { Text("Reset Password") },
            text = {
                Column {
                    Text("Enter your email to receive a password reset link.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { 
                            resetEmail = it
                            resetErrorText = null
                        },
                        label = { Text("Email") },
                        singleLine = true,
                        isError = resetErrorText != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (resetErrorText != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = resetErrorText!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { handlePasswordReset() }) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showResetDialog = false
                        resetErrorText = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
