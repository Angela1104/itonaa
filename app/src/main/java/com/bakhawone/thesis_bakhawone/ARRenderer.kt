package com.bakhawone.thesis_bakhawone

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Display
import android.view.WindowManager
import com.google.ar.core.*
import com.google.ar.core.exceptions.NotYetAvailableException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos

class ARRenderer(
    private val session: Session,
    private val deviceStartLat: Double,
    private val deviceStartLon: Double,
    private val context: Context,
    // ✅ Visibility callback — called when boundary becomes visible/hidden
    private val onBoundaryVisibleChanged: ((Boolean) -> Unit)? = null
) : GLSurfaceView.Renderer {

    private var centerLat: Double? = null
    private var centerLon: Double? = null
    private var anchor: Anchor? = null
    private var planeFound = false
    private var boundaryVisible = false // ✅ track current visibility state

    private lateinit var surfaceView: GLSurfaceView
    private val cameraBackgroundRenderer = CameraBackgroundRenderer()
    private val boundaryRenderer = BoundaryRenderer()
    private var detectedPlane: Plane? = null
    private var lastFrame: Frame? = null
    private var lastCamera: Camera? = null
    private val boundaryRadiusMeters = 17.841f

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
        lastFrame = frame
        lastCamera = camera

        cameraBackgroundRenderer.draw(frame)

        // Feed frames to trunk detector when detection is active
        try {
            val activity = context as? ARActivity
            if (activity?.detectionRunning == true) {
                try {
                    val detector = activity.trunkDetector
                    if (detector != null && detector.canAcceptFrame()) {
                        val image = frame.acquireCameraImage()
                        detector.analyzeImage(image)
                    }
                } catch (e: NotYetAvailableException) {
                    // image not available — skip this frame
                } catch (e: Exception) {
                    // ensure no leaks
                    try { /* ignore */ } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) { /* safe cast guard */ }

        if (camera.trackingState != TrackingState.TRACKING) return

        // ✅ Detect first horizontal plane
        if (!planeFound) {
            for (plane in session.getAllTrackables(Plane::class.java)) {
                if (plane.trackingState == TrackingState.TRACKING &&
                    plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING) {
                    detectedPlane = plane
                    planeFound = true
                    Log.d("ARRenderer", "Plane detected! Center: ${plane.centerPose}")
                    break
                }
            }
        }

        // ✅ Create anchor once the plane and centerpoint are both known
        if (planeFound && anchor == null && centerLat != null && centerLon != null) {
            createStableAnchor()
        }

        // ✅ Check if anchor is still valid
        if (anchor != null && anchor!!.trackingState != TrackingState.TRACKING) {
            Log.d("ARRenderer", "Anchor lost tracking, recreating...")
            anchor = null
            if (boundaryVisible) {
                boundaryVisible = false
                onBoundaryVisibleChanged?.invoke(false)
            }
        }

        // ✅ Draw only if the anchor exists and is tracking
        if (anchor != null && anchor!!.trackingState == TrackingState.TRACKING) {
            boundaryRenderer.draw(anchor!!.pose, camera)
            if (!boundaryVisible) {
                boundaryVisible = true
                onBoundaryVisibleChanged?.invoke(true)
                Log.d("ARRenderer", "Boundary visible")
            }
        } else {
            if (boundaryVisible) {
                boundaryVisible = false
                onBoundaryVisibleChanged?.invoke(false)
                Log.d("ARRenderer", "Boundary hidden")
            }
        }
    }

    private fun createStableAnchor() {
        val plane = detectedPlane ?: return
        val centerPose = plane.centerPose

        // Convert GPS difference to local flat coordinates (meters)
        val latOffset = ((centerLat!! - deviceStartLat) * 111000).toFloat()
        val lonOffset = ((centerLon!! - deviceStartLon) * 111000 *
                cos(deviceStartLat * Math.PI / 180)).toFloat()

        // Place the anchor directly on the plane surface (Y from plane)
        val adjustedPose = centerPose.compose(Pose.makeTranslation(lonOffset, 0f, -latOffset))
        anchor = session.createAnchor(adjustedPose)

        Log.d("ARRenderer", "Stable anchor created at lat: $centerLat, lon: $centerLon")
        Log.d("ARRenderer", "Device start: lat: $deviceStartLat, lon: $deviceStartLon")
        Log.d("ARRenderer", "Offsets: lat: $latOffset, lon: $lonOffset")
    }

    // === Public helpers for detection enrichment ===
    fun getFxPixels(): Float? {
        val camera = lastCamera ?: return null
        return try {
            val intrinsics = camera.imageIntrinsics
            intrinsics.focalLength[0] // fx in pixels
        } catch (_: Exception) { null }
    }

    fun worldPointAndDepthFromScreen(screenX: Float, screenY: Float): Pair<FloatArray, Float>? {
        val frame = lastFrame ?: return null
        val anchorPose = anchor?.pose ?: return null
        val hitList = try { frame.hitTest(screenX, screenY) } catch (_: Exception) { emptyList() }
        val best = hitList.firstOrNull { it.trackable is Plane && (it.trackable as Plane).isPoseInPolygon(it.hitPose) }
            ?: return null
        val cameraPose = frame.camera.displayOrientedPose
        val hitPose = best.hitPose
        val dx = hitPose.tx() - cameraPose.tx()
        val dy = hitPose.ty() - cameraPose.ty()
        val dz = hitPose.tz() - cameraPose.tz()
        val depth = kotlin.math.sqrt(dx*dx + dy*dy + dz*dz)
        // vector relative to anchor origin in world space
        val anchorInv = floatArrayOf(
            0f,0f,0f,0f, 0f,0f,0f,0f, 0f,0f,0f,0f, 0f,0f,0f
        )
        val anchorMatrix = FloatArray(16)
        anchorPose.toMatrix(anchorMatrix, 0)
        android.opengl.Matrix.invertM(anchorInv, 0, anchorMatrix, 0)
        val hitMatrix = FloatArray(16)
        hitPose.toMatrix(hitMatrix, 0)
        val local = FloatArray(16)
        android.opengl.Matrix.multiplyMM(local, 0, anchorInv, 0, hitMatrix, 0)
        val localVec = floatArrayOf(local[12], local[13], local[14])
        return localVec to depth
    }

    fun isLocalPointInsideBoundary(localPoint: FloatArray): Boolean {
        // distance in XZ plane from center
        val d = kotlin.math.sqrt(localPoint[0]*localPoint[0] + localPoint[2]*localPoint[2])
        return d <= boundaryRadiusMeters
    }
}
