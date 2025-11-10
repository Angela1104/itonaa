package com.bakhawone.thesis_bakhawone

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakhawone.thesis_bakhawone.ui.theme.ThesisbakhawoneTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import java.util.regex.Pattern

class SignupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ThesisbakhawoneTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SignupScreen(
                        onSignupSuccess = {
                            val intent = Intent(this, DashboardActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        },
                        onNavigateBackToLogin = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SignupScreen(
    onSignupSuccess: () -> Unit,
    onNavigateBackToLogin: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    fun isValidEmail(email: String): Boolean {
        val emailPattern = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"
        )
        return emailPattern.matcher(email.trim()).matches()
    }

    fun validateInputs(): String? {
        return when {
            name.isBlank() || email.isBlank() || organization.isBlank() || 
            password.isBlank() || confirmPassword.isBlank() -> {
                "Please fill in all fields"
            }
            !isValidEmail(email) -> {
                "Please enter a valid email address"
            }
            password.length < 6 -> {
                "Password must be at least 6 characters"
            }
            password != confirmPassword -> {
                "Passwords do not match"
            }
            else -> null
        }
    }

    fun handleSignup() {
        val validationError = validateInputs()
        if (validationError != null) {
            errorText = validationError
            return
        }

        isLoading = true
        errorText = null
        
        val trimmedEmail = email.trim()
        val trimmedName = name.trim()
        val trimmedOrganization = organization.trim()

        auth.createUserWithEmailAndPassword(trimmedEmail, password)
            .addOnCompleteListener { createTask ->
                if (!createTask.isSuccessful) {
                    isLoading = false
                    errorText = createTask.exception?.localizedMessage ?: "Sign up failed"
                    return@addOnCompleteListener
                }

                val user = auth.currentUser
                if (user == null) {
                    isLoading = false
                    errorText = "Failed to create user account"
                    return@addOnCompleteListener
                }

                val profileUpdate = UserProfileChangeRequest.Builder()
                    .setDisplayName(trimmedName)
                    .build()

                user.updateProfile(profileUpdate)
                    .addOnCompleteListener { profileTask ->
                        if (!profileTask.isSuccessful) {
                            errorText = "Account created but profile update failed"
                        }

                        val userDoc = mapOf(
                            "name" to trimmedName,
                            "email" to trimmedEmail,
                            "organization" to trimmedOrganization
                        )

                        db.collection("users").document(user.uid)
                            .set(userDoc)
                            .addOnCompleteListener { writeTask ->
                                isLoading = false
                                
                                if (writeTask.isSuccessful) {
                                    onSignupSuccess()
                                } else {
                                    errorText = writeTask.exception?.localizedMessage 
                                        ?: "Account created but failed to save profile"
                                }
                            }
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
                    .size(96.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Create account",
                style = MaterialTheme.typography.headlineSmall,
                fontSize = 22.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { 
                    name = it
                    errorText = null
                },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                value = organization,
                onValueChange = { 
                    organization = it
                    errorText = null
                },
                label = { Text("Organization") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
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
                    imeAction = ImeAction.Next
                ),
                trailingIcon = {
                    val visibilityIcon = if (passwordVisible) 
                        Icons.Filled.VisibilityOff 
                    else 
                        Icons.Filled.Visibility
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

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { 
                    confirmPassword = it
                    errorText = null
                },
                label = { Text("Confirm Password") },
                singleLine = true,
                visualTransformation = if (confirmPasswordVisible) 
                    VisualTransformation.None 
                else 
                    PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                trailingIcon = {
                    val visibilityIcon = if (confirmPasswordVisible) 
                        Icons.Filled.VisibilityOff 
                    else 
                        Icons.Filled.Visibility
                    val description = if (confirmPasswordVisible) 
                        "Hide password" 
                    else 
                        "Show password"
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = visibilityIcon, 
                            contentDescription = description
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (errorText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorText!!, 
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { handleSignup() },
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
                    Text(text = "Sign up")
                }
            }

            TextButton(
                onClick = { onNavigateBackToLogin() }, 
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Already have an account? Sign in")
            }
        }
    }
}
