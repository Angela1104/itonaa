package com.bakhawone.thesis_bakhawone

// Android Framework
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

// Compose Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

// Compose Foundation
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions

// Compose Material 3
import androidx.compose.material3.*

// Compose Runtime
import androidx.compose.runtime.*

// Compose UI Components
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Firebase Services
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

// App Theme
import com.bakhawone.thesis_bakhawone.ui.theme.ThesisbakhawoneTheme

class SignupActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setContent {
            ThesisbakhawoneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SignupScreen(auth = auth, db = db)
                }
            }
        }
    }
}

@Composable
fun SignupScreen(auth: FirebaseAuth, db: FirebaseFirestore) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.bakhawone),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(250.dp)
                .padding(bottom = 32.dp)
        )

        Text(
            text = "Create Account",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Full Name", fontSize = 16.sp) },
                singleLine = true,
                shape = RoundedCornerShape(50.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("Email", fontSize = 16.sp) },
                singleLine = true,
                shape = RoundedCornerShape(50.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("Password", fontSize = 16.sp) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(50.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                placeholder = { Text("Confirm Password", fontSize = 16.sp) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(50.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            )

            // Password requirements hint
            if (password.isNotEmpty()) {
                Text(
                    text = if (password.length < 6) "Password must be at least 6 characters"
                    else "✓ Password meets requirements",
                    fontSize = 12.sp,
                    color = if (password.length < 6) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Password match indicator
            if (password.isNotEmpty() && confirmPassword.isNotEmpty()) {
                Text(
                    text = if (password == confirmPassword) "✓ Passwords match"
                    else "✗ Passwords do not match",
                    fontSize = 12.sp,
                    color = if (password == confirmPassword) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (isLoading) return@Button

                    val trimmedName = name.trim()
                    val trimmedEmail = email.trim()
                    val trimmedPassword = password.trim()
                    val trimmedConfirmPassword = confirmPassword.trim()

                    // Validation
                    when {
                        trimmedName.isEmpty() || trimmedEmail.isEmpty() ||
                                trimmedPassword.isEmpty() || trimmedConfirmPassword.isEmpty() -> {
                            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        trimmedPassword != trimmedConfirmPassword -> {
                            Toast.makeText(context, "Passwords don't match", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        trimmedPassword.length < 6 -> {
                            Toast.makeText(context, "Password should be at least 6 characters", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        !isValidEmail(trimmedEmail) -> {
                            Toast.makeText(context, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                    }

                    isLoading = true

                    auth.createUserWithEmailAndPassword(trimmedEmail, trimmedPassword)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                if (user != null) {
                                    // Update user profile with display name
                                    val profileUpdates = UserProfileChangeRequest.Builder()
                                        .setDisplayName(trimmedName)
                                        .build()

                                    user.updateProfile(profileUpdates)
                                        .addOnCompleteListener { profileTask ->
                                            if (profileTask.isSuccessful) {
                                                // Store additional user data in Firestore
                                                val userData = hashMapOf(
                                                    "name" to trimmedName,
                                                    "email" to trimmedEmail,
                                                    "createdAt" to com.google.firebase.Timestamp.now(),
                                                    "userId" to user.uid
                                                )

                                                db.collection("users")
                                                    .document(user.uid)
                                                    .set(userData)
                                                    .addOnSuccessListener {
                                                        isLoading = false
                                                        Toast.makeText(
                                                            context,
                                                            "Account created successfully!",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        activity?.startActivity(
                                                            Intent(activity, MainActivity::class.java)
                                                        )
                                                        activity?.finish()
                                                    }
                                                    .addOnFailureListener { e ->
                                                        isLoading = false
                                                        // If Firestore fails, delete the auth user
                                                        user.delete()
                                                        Toast.makeText(
                                                            context,
                                                            "Failed to save user data: ${e.message}",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                            } else {
                                                isLoading = false
                                                user.delete()
                                                Toast.makeText(
                                                    context,
                                                    "Failed to create user profile",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                } else {
                                    isLoading = false
                                    Toast.makeText(context, "User creation failed", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                isLoading = false
                                val errorMessage = when {
                                    task.exception?.message?.contains("email address is already") == true ->
                                        "Email is already registered"
                                    task.exception?.message?.contains("badly formatted") == true ->
                                        "Invalid email format"
                                    else -> task.exception?.localizedMessage ?: "Signup failed"
                                }
                                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                },
                enabled = !isLoading && name.isNotBlank() && email.isNotBlank() &&
                        password.isNotBlank() && confirmPassword.isNotBlank() &&
                        password.length >= 6 && password == confirmPassword,
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(vertical = 16.dp)
                    .height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "CREATE ACCOUNT",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Text(
                text = "Already have an account? Log in",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .clickable {
                        activity?.startActivity(Intent(activity, MainActivity::class.java))
                        activity?.finish()
                    }
            )
        }
    }
}

// Email validation function
private fun isValidEmail(email: String): Boolean {
    val emailRegex = "^[A-Za-z](.*)([@]{1})(.{1,})(\\.)(.{1,})".toRegex()
    return email.matches(emailRegex)
}