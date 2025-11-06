package com.bakhawone.thesis_bakhawone.ui.theme

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Base64
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import coil.compose.AsyncImage
import com.bakhawone.thesis_bakhawone.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

data class UserData(
    val name: String,
    val email: String,
    val organization: String,
    val photoUrl: String?
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
    var isSavingPhoto by remember { mutableStateOf(false) }

    // Determine which permission to request based on Android version
    val photoPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // Image picker launcher
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val currentUser = auth.currentUser
        if (uri != null && currentUser != null) {
            isSavingPhoto = true
            // Convert image URI to base64 data URL with compression
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    // Decode bitmap with sampling to reduce memory
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(inputStream, null, options)
                    inputStream.close()
                    
                    // Calculate sample size to resize image (max 800px on largest side)
                    // Sample size must be a power of 2
                    val maxSize = 800
                    val scale = when {
                        options.outWidth > options.outHeight -> options.outWidth / maxSize
                        else -> options.outHeight / maxSize
                    }
                    val sampleSize = when {
                        scale <= 1 -> 1
                        scale <= 2 -> 2
                        scale <= 4 -> 4
                        scale <= 8 -> 8
                        else -> 16
                    }
                    
                    // Decode with sampling
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    val inputStream2: InputStream? = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
                    inputStream2?.close()
                    
                    if (bitmap != null) {
                        // Compress bitmap to JPEG (quality 80)
                        val outputStream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                        val compressedBytes = outputStream.toByteArray()
                        outputStream.close()
                        bitmap.recycle()
                        
                        android.util.Log.d("ProfileScreen", "Image compressed: ${compressedBytes.size} bytes")
                        
                        // Convert to base64
                        val base64String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Base64.getEncoder().encodeToString(compressedBytes)
                        } else {
                            android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
                        }
                        
                        // Create data URL
                        val dataUrl = "data:image/jpeg;base64,$base64String"
                        
                        android.util.Log.d("ProfileScreen", "Data URL length: ${dataUrl.length}")
                        
                        // Save to Firestore
                        db.collection("users").document(currentUser.uid)
                            .set(mapOf("photoUrl" to dataUrl), SetOptions.merge())
                            .addOnSuccessListener {
                                userData = userData?.copy(photoUrl = dataUrl)
                                isSavingPhoto = false
                                android.util.Log.d("ProfileScreen", "Photo saved successfully")
                                Toast.makeText(context, "Profile photo saved", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                isSavingPhoto = false
                                android.util.Log.e("ProfileScreen", "Failed to save photo", e)
                                Toast.makeText(context, "Failed to save photo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        isSavingPhoto = false
                        android.util.Log.e("ProfileScreen", "Could not decode bitmap")
                        Toast.makeText(context, "Could not process image", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    isSavingPhoto = false
                    Toast.makeText(context, "Could not read image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                isSavingPhoto = false
                android.util.Log.e("ProfileScreen", "Error processing image", e)
                Toast.makeText(context, "Error processing image: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, launch image picker
            imagePicker.launch("image/*")
        } else {
            Toast.makeText(
                context,
                "Photo permission is required to set profile picture",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Function to handle photo selection click - checks permission first
    val handlePhotoSelectionClick = {
        // Check if permission is already granted
        val hasPermission = ContextCompat.checkSelfPermission(context, photoPermission) == PackageManager.PERMISSION_GRANTED
        
        if (hasPermission) {
            // Permission already granted, launch image picker directly
            imagePicker.launch("image/*")
        } else {
            // Request permission first
            permissionLauncher.launch(photoPermission)
        }
    }

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
                            organization = document.getString("organization") ?: " ",
                            photoUrl = document.getString("photoUrl")
                        )
                    } else {
                        UserData(
                            name = currentUser.displayName ?: "Not set",
                            email = currentUser.email ?: "Not set",
                            organization = "Not set",
                            photoUrl = currentUser.photoUrl?.toString()
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
                    verticalArrangement = Arrangement.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                ) {
                    // Picture frame with photo selection
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0E0E0))
                            .clickable(enabled = !isSavingPhoto, onClick = handlePhotoSelectionClick),
                        contentAlignment = Alignment.Center
                    ) {
                        if (userData?.photoUrl != null && userData?.photoUrl!!.isNotBlank()) {
                            // Decode base64 data URL to ImageBitmap
                            val imageBitmap = remember(userData?.photoUrl) {
                                try {
                                    val dataUrl = userData?.photoUrl ?: ""
                                    if (dataUrl.startsWith("data:image")) {
                                        val base64String = dataUrl.substringAfter("base64,")
                                        val imageBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            Base64.getDecoder().decode(base64String)
                                        } else {
                                            android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
                                        }
                                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                        bitmap?.asImageBitmap()
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("ProfileScreen", "Error decoding image: ${e.message}", e)
                                    null
                                }
                            }
                            
                            if (imageBitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = imageBitmap,
                                    contentDescription = "Profile Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                // Fallback to AsyncImage if manual decode fails
                                AsyncImage(
                                    model = userData?.photoUrl,
                                    contentDescription = "Profile Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    onError = {
                                        android.util.Log.e("ProfileScreen", "Failed to load image: ${it.result.throwable}")
                                    },
                                    onSuccess = {
                                        android.util.Log.d("ProfileScreen", "Image loaded successfully")
                                    }
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "No Photo",
                                modifier = Modifier.size(64.dp),
                                tint = Color.DarkGray
                            )
                        }
                        if (isSavingPhoto) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Name (bold) with separator
                    Text(
                        text = userData?.name ?: "Not set",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Divider(modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(), color = Color.LightGray)

                    // Section: Personal information
                    Text(
                        text = "Personal information",
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 20.sp, // Change this value to adjust size
                        color = Color.Gray,
                        modifier = Modifier
                            .padding(top = 16.dp, bottom = 16.dp)
                            .align(Alignment.Start)
                    )

                    // Email (bold)
                    Column(modifier = Modifier.align(Alignment.Start)) {
                        Text(
                            text = "Email",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray
                        )
                        Text(
                            text = userData?.email ?: "Not set",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Organization (bold)
                    Column(modifier = Modifier.align(Alignment.Start)) {
                        Text(
                            text = "Organization",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray
                        )
                        Text(
                            text = userData?.organization ?: "Not set",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.Black
                        )
                    }

                    // Logout button (center)
                    val activity = context as? Activity
                    Button(
                        onClick = {
                            auth.signOut()
                            Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                            context.startActivity(android.content.Intent(context, LoginActivity::class.java))
                            activity?.finish()
                        },
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        Text("Logout")
                    }
                }
            }
        }
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
        leadingIcon = { Icon(imageVector = icon, contentDescription = label, tint = Color.Black) },
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
