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
    private val context: Context   // ✅ Added context so we can access display rotation
) : GLSurfaceView.Renderer {

    private var centerLat: Double? = null
    private var centerLon: Double? = null
    private var anchor: Anchor? = null

    private lateinit var surfaceView: GLSurfaceView
    private val cameraBackgroundRenderer = CameraBackgroundRenderer()
    private val boundaryRenderer = BoundaryRenderer()
    private var deviceStartPose: Pose? = null

    // ✅ Add display reference
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
        // ✅ Inform ARCore of current rotation and viewport
        session.setDisplayGeometry(display.rotation, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val frame = session.update()
        val camera = frame.camera

        // Draw the camera background correctly oriented
        cameraBackgroundRenderer.draw(frame)

        if (camera.trackingState != TrackingState.TRACKING) return
        if (deviceStartPose == null) deviceStartPose = camera.displayOrientedPose

        if (centerLat != null && centerLon != null && anchor == null && deviceStartPose != null)
            createStableAnchor()

        anchor?.let { boundaryRenderer.draw(it.pose) }
    }

    private fun createStableAnchor() {
        val devicePose = deviceStartPose ?: return
        val latOffset = ((centerLat!! - deviceStartLat) * 111000).toFloat()
        val lonOffset = ((centerLon!! - deviceStartLon) * 111000 * cos(deviceStartLat * Math.PI / 180)).toFloat()
        val forwardDistance = 1.5f
        val heightOffset = 0.15f
        val anchorPose = Pose.makeTranslation(lonOffset, heightOffset, -latOffset - forwardDistance)
        anchor = session.createAnchor(devicePose.compose(anchorPose))
        Log.d("ARRenderer", "Stable anchor created")
    }
}
