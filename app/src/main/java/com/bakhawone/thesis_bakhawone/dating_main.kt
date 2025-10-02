package com.bakhawone.thesis_bakhawone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakhawone.thesis_bakhawone.ui.theme.ThesisBakhawoneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThesisBakhawoneTheme {
                MainScreen {
                    // Navigate to CamActivity when button is clicked
                    val intent = Intent(this, CamActivity::class.java)
                    startActivity(intent)
                }
            }
        }
    }
}

@Composable
fun MainScreen(onStartScanClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(20.dp)
    ) {
        Button(
            onClick = onStartScanClick,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            Text(
                text = "Start Scanning",
                fontSize = 18.sp,
                color = Color.White
            )
        }
    }
}
