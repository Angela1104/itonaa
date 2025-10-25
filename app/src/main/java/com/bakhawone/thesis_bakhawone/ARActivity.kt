package com.bakhawone.thesis_bakhawone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ARActivity : ComponentActivity() {

    private var arSession: Session? = null
    private lateinit var renderer: ARRenderer
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var deviceStartLat: Double = 0.0
    private var deviceStartLon: Double = 0.0
    private val CAMERA_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deviceStartLat = intent.getDoubleExtra("deviceStartLat", 0.0)
        deviceStartLon = intent.getDoubleExtra("deviceStartLon", 0.0)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            initARSession()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initARSession()
            } else {
                Toast.makeText(this, "Camera permission required for AR", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun initARSession() {
        try {
            arSession = Session(this)
        } catch (e: UnavailableArcoreNotInstalledException) {
            Toast.makeText(this, "Please install ARCore", Toast.LENGTH_LONG).show()
            finish()
            return
        } catch (e: UnavailableDeviceNotCompatibleException) {
            Toast.makeText(this, "This device is not AR compatible", Toast.LENGTH_LONG).show()
            finish()
            return
        } catch (e: Exception) {
            finish()
            return
        }

        val config = Config(arSession)
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        arSession?.configure(config)

        // ✅ Pass context in constructor (required by new ARRenderer)
        renderer = ARRenderer(arSession!!, deviceStartLat, deviceStartLon, this)

        // ✅ Call the updated getGLSurfaceView() (no arguments)
        setContentView(renderer.getGLSurfaceView())

        fetchLatestCenterpoint()
    }

    private fun fetchLatestCenterpoint() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users")
            .document(userId)
            .collection("centerpoints")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val lat = doc.getDouble("latitude") ?: 0.0
                    val lon = doc.getDouble("longitude") ?: 0.0
                    renderer.setCenterPoint(lat, lon)
                }
            }
    }

    override fun onResume() {
        super.onResume()
        try {
            arSession?.resume()
            renderer.onResume()
        } catch (e: CameraNotAvailableException) {
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        renderer.onPause()
        arSession?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        renderer.onPause()
        arSession?.close()
        arSession = null
    }
}
