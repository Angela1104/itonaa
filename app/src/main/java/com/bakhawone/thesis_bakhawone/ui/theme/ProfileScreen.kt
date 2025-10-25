package com.bakhawone.thesis_bakhawone.ui.theme

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class UserData(
    val name: String,
    val email: String,
    val organization: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    var userData by remember { mutableStateOf<UserData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showEditOrganizationDialog by remember { mutableStateOf(false) }
    var tempOrganization by remember { mutableStateOf("") }

    // Fetch Firestore data
    LaunchedEffect(Unit) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    userData = if (document.exists()) {
                        UserData(
                            name = document.getString("name") ?: " ",
                            email = document.getString("email") ?: currentUser.email ?: " ",
                            organization = document.getString("organization") ?: " "
                        )
                    } else {
                        UserData(
                            name = currentUser.displayName ?: "Not set",
                            email = currentUser.email ?: "Not set",
                            organization = "Not set"
                        )
                    }
                    isLoading = false
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to load user data", Toast.LENGTH_SHORT).show()
                    isLoading = false
                }
        } else {
            Toast.makeText(context, "No user logged in", Toast.LENGTH_SHORT).show()
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF7A876F)
                )
            )
        },
        containerColor = Color(0xFFF7F7F2)
    ) { padding ->

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    // Name
                    Text(
                        text = userData?.name ?: "Not set",
                        fontSize = 37.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    // Email & Organization fields
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        ProfileRoundedField(
                            label = "Email",
                            value = userData?.email ?: "Not set",
                            icon = Icons.Default.Email
                        )

                        ProfileRoundedField(
                            label = "Organization",
                            value = userData?.organization ?: "Not set",
                            icon = Icons.Default.Work,
                            trailingIcon = {
                                IconButton(onClick = {
                                    tempOrganization = userData?.organization ?: ""
                                    showEditOrganizationDialog = true
                                }) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "Edit",
                                        tint = Color.Gray
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Dialog for editing organization
    if (showEditOrganizationDialog) {
        EditOrganizationDialog(
            organization = tempOrganization,
            onDismiss = { showEditOrganizationDialog = false },
            onSave = { newOrg ->
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    db.collection("users").document(currentUser.uid)
                        .update("organization", newOrg)
                        .addOnSuccessListener {
                            userData = userData?.copy(organization = newOrg)
                            Toast.makeText(context, "Organization updated!", Toast.LENGTH_SHORT).show()
                        }
                }
                showEditOrganizationDialog = false
            }
        )
    }
}

@Composable
fun ProfileRoundedField(
    label: String,
    value: String,
    icon: ImageVector,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label, fontSize = 15.sp, color = Color.Black) },
        leadingIcon = { Icon(icon, contentDescription = label, tint = Color.Black) },
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(50.dp),
        singleLine = true,
        enabled = false,
        textStyle = TextStyle.Default.copy(
            fontSize = 18.sp,
            color = Color.Black
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Black,
            unfocusedBorderColor = Color.Black,
            cursorColor = Color.Black,
            focusedLabelColor = Color.Black,
            unfocusedLabelColor = Color.Black,
            disabledTextColor = Color.Black,
            disabledBorderColor = Color.Black,
            disabledLabelColor = Color.Black,
            disabledLeadingIconColor = Color.Black
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}

@Composable
fun EditOrganizationDialog(
    organization: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var tempOrg by remember { mutableStateOf(organization) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Edit Organization") },
        text = {
            OutlinedTextField(
                value = tempOrg,
                onValueChange = { tempOrg = it },
                label = { Text("Organization Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { if (tempOrg.isNotBlank()) onSave(tempOrg) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Cancel")
            }
        }
    )
}
