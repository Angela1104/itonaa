package com.bakhawone.thesis_bakhawone

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Display
import android.view.WindowManager
import com.google.ar.core.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos

class ARRenderer(
    private val session: Session,
    private val deviceStartLat: Double,
    private val deviceStartLon: Double,
    private val context: Context
) : GLSurfaceView.Renderer {

    private var centerLat: Double? = null
    private var centerLon: Double? = null
    private var anchor: Anchor? = null
    private var planeFound = false

    private lateinit var surfaceView: GLSurfaceView
    private val cameraBackgroundRenderer = CameraBackgroundRenderer()
    private val boundaryRenderer = BoundaryRenderer()
    private var detectedPlane: Plane? = null

    // Get display rotation for proper AR orientation
    private val display: Display by lazy {
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    }

    fun getGLSurfaceView(): GLSurfaceView {
        surfaceView = GLSurfaceView(context)
        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setRenderer(this)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        return surfaceView
    }

    fun onResume() = surfaceView.onResume()
    fun onPause() = surfaceView.onPause()
    fun setCenterPoint(lat: Double, lon: Double) { centerLat = lat; centerLon = lon }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        cameraBackgroundRenderer.createOnGlThread(session)
        boundaryRenderer.init()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        session.setDisplayGeometry(display.rotation, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val frame = session.update()
        val camera = frame.camera

        cameraBackgroundRenderer.draw(frame)

        if (camera.trackingState != TrackingState.TRACKING) return

        // ✅ Detect first horizontal plane
        if (!planeFound) {
            for (plane in session.getAllTrackables(Plane::class.java)) {
                if (plane.trackingState == TrackingState.TRACKING &&
                    plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING) {
                    detectedPlane = plane
                    planeFound = true
                    Log.d("ARRenderer", "Plane detected!")
                    break
                }
            }
        }

        // ✅ Create anchor once the plane and centerpoint are both known
        if (planeFound && anchor == null && centerLat != null && centerLon != null) {
            createStableAnchor()
        }

        // ✅ Draw only if the anchor exists (fixed in space)
        anchor?.let { boundaryRenderer.draw(it.pose, camera) }
    }

    private fun createStableAnchor() {
        val plane = detectedPlane ?: return
        val centerPose = plane.centerPose

        // Convert GPS difference to local flat coordinates (meters)
        val latOffset = ((centerLat!! - deviceStartLat) * 111000).toFloat()
        val lonOffset = ((centerLon!! - deviceStartLon) * 111000 * cos(deviceStartLat * Math.PI / 180)).toFloat()

        // ✅ Place the anchor directly on the plane surface (Y from plane)
        val adjustedPose = centerPose.compose(
            Pose.makeTranslation(lonOffset, 0f, -latOffset)
        )

        anchor = session.createAnchor(adjustedPose)
        Log.d("ARRenderer", "Stable anchor created on detected surface")
    }
}
