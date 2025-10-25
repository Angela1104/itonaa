package com.bakhawone.thesis_bakhawone

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.*

class ARActivity : ComponentActivity() {
    private var arSession: Session? = null
    private lateinit var renderer: ARRenderer
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            arSession = Session(this)
        } catch (e: UnavailableArcoreNotInstalledException) {
            Toast.makeText(this, "ARCore not installed.", Toast.LENGTH_LONG).show()
            finish()
            return
        } catch (e: UnavailableDeviceNotCompatibleException) {
            Toast.makeText(this, "Device not compatible with ARCore.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        renderer = ARRenderer(arSession!!)

        setContentView(renderer.getGLSurfaceView(this))

        // Fetch centerpoint from Firestore and initialize AR
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
                    Log.d("ARActivity", "Loaded centerpoint: $lat, $lon")

                    // Pass the coordinates to ARRenderer
                    renderer.setCenterPoint(lat, lon)
                    Toast.makeText(this, "Centerpoint loaded for AR scene", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No centerpoint found in database", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ARActivity", "Failed to load centerpoint", e)
                Toast.makeText(this, "Failed to load centerpoint", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onResume() {
        super.onResume()
        try {
            arSession?.resume()
            renderer.onResume()
        } catch (e: CameraNotAvailableException) {
            e.printStackTrace()
            arSession = null
        }
    }

    override fun onPause() {
        super.onPause()
        renderer.onPause()
        arSession?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        arSession?.close()
        arSession = null
    }
}
