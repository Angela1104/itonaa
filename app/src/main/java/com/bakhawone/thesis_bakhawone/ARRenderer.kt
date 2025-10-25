package com.bakhawone.thesis_bakhawone

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.google.ar.core.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class ARRenderer(private val session: Session) : GLSurfaceView.Renderer {

    private var centerLat: Double? = null
    private var centerLon: Double? = null
    private var anchor: Anchor? = null

    private lateinit var surfaceView: GLSurfaceView
    private val cameraBackgroundRenderer = CameraBackgroundRenderer()
    private val boundaryRenderer = BoundaryRenderer()

    fun getGLSurfaceView(context: Context): GLSurfaceView {
        surfaceView = GLSurfaceView(context)
        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setRenderer(this)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        return surfaceView
    }

    fun onResume() {
        surfaceView.onResume()
    }

    fun onPause() {
        surfaceView.onPause()
    }

    /** Receives the Firestore centerpoint */
    fun setCenterPoint(lat: Double, lon: Double) {
        centerLat = lat
        centerLon = lon
        Log.d("ARRenderer", "Centerpoint set: $lat, $lon")
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        cameraBackgroundRenderer.createOnGlThread()
        boundaryRenderer.init()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val frame = session.update()
        val camera = frame.camera
        cameraBackgroundRenderer.draw(frame)

        if (camera.trackingState != TrackingState.TRACKING) return

        // Once we have a centerpoint, anchor it once
        if (centerLat != null && centerLon != null && anchor == null) {
            createAnchorAtCurrentPose(camera)
        }

        // Draw the boundary around the anchor
        anchor?.let {
            val pose = it.pose
            boundaryRenderer.draw(pose)
        }
    }

    private fun createAnchorAtCurrentPose(camera: Camera) {
        try {
            val framePose = camera.displayOrientedPose
            anchor = session.createAnchor(framePose)
            Log.d("ARRenderer", "Anchor created at AR center for lat/lon: $centerLat, $centerLon")
        } catch (e: Exception) {
            Log.e("ARRenderer", "Failed to create anchor: ${e.message}")
        }
    }
}
