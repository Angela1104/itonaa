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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val configuration = LocalConfiguration.current
    val isTablet = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    var userData by remember { mutableStateOf<UserData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showEditOrganizationDialog by remember { mutableStateOf(false) }
    var tempOrganization by remember { mutableStateOf("") }
    var isSavingPhoto by remember { mutableStateOf(false) }

    val photoPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val currentUser = auth.currentUser
        if (uri != null && currentUser != null) {
            isSavingPhoto = true
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(inputStream, null, options)
                    inputStream.close()
                    
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
                    
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    val inputStream2: InputStream? = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
                    inputStream2?.close()
                    
                    if (bitmap != null) {
                        val outputStream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                        val compressedBytes = outputStream.toByteArray()
                        outputStream.close()
                        bitmap.recycle()
                        
                        android.util.Log.d("ProfileScreen", "Image compressed: ${compressedBytes.size} bytes")
                        
                        val base64String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Base64.getEncoder().encodeToString(compressedBytes)
                        } else {
                            android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
                        }
                        
                        val dataUrl = "data:image/jpeg;base64,$base64String"
                        
                        android.util.Log.d("ProfileScreen", "Data URL length: ${dataUrl.length}")
                        
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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            imagePicker.launch("image/*")
        } else {
            Toast.makeText(
                context,
                "Photo permission is required to set profile picture",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val handlePhotoSelectionClick = {
        val hasPermission = ContextCompat.checkSelfPermission(context, photoPermission) == PackageManager.PERMISSION_GRANTED
        
        if (hasPermission) {
            imagePicker.launch("image/*")
        } else {
            permissionLauncher.launch(photoPermission)
        }
    }

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
                        .padding(
                            horizontal = if (isTablet) 32.dp else 24.dp, 
                            vertical = if (isLandscape) 16.dp else 24.dp
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isTablet) 180.dp else 150.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0E0E0))
                            .clickable(enabled = !isSavingPhoto, onClick = handlePhotoSelectionClick),
                        contentAlignment = Alignment.Center
                    ) {
                        if (userData?.photoUrl != null && userData?.photoUrl!!.isNotBlank()) {
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

                    Text(
                        text = userData?.name ?: "Not set",
                        fontSize = if (isTablet) 36.sp else 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    Divider(modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(), color = Color.LightGray)

                    Text(
                        text = "Personal information",
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = if (isTablet) 22.sp else 20.sp,
                        color = Color.Gray,
                        modifier = Modifier
                            .padding(top = 16.dp, bottom = 16.dp)
                            .align(Alignment.Start)
                    )

                    Column(modifier = Modifier
                        .align(Alignment.Start)
                        .fillMaxWidth()) {
                        Text(
                            text = "Email",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                            fontSize = if (isTablet) 15.sp else 14.sp
                        )
                        Text(
                            text = userData?.email ?: "Not set",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isTablet) 22.sp else 20.sp,
                            color = Color.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isTablet) 16.dp else 12.dp))

                    Column(modifier = Modifier
                        .align(Alignment.Start)
                        .fillMaxWidth()) {
                        Text(
                            text = "Organization",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                            fontSize = if (isTablet) 15.sp else 14.sp
                        )
                        Text(
                            text = userData?.organization ?: "Not set",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isTablet) 22.sp else 20.sp,
                            color = Color.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

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
