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

class ARRenderer(
    private val session: Session,
    private val context: Context,
    // ✅ Visibility callback — called when boundary becomes visible/hidden
    private val onBoundaryVisibleChanged: ((Boolean) -> Unit)? = null
) : GLSurfaceView.Renderer {

    private var anchor: Anchor? = null
    private var boundaryVisible = false // ✅ track current visibility state

    private lateinit var surfaceView: GLSurfaceView
    private val cameraBackgroundRenderer = CameraBackgroundRenderer()
    private val boundaryRenderer = BoundaryRenderer()
    private val planeRenderer = PlaneRenderer()
    private var lastFrame: Frame? = null
    private var lastCamera: Camera? = null
    private val boundaryRadiusMeters = 17.841f
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0

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
    
    /**
     * Set centerpoint from a tapped position on a plane.
     * Creates an anchor at the hit position.
     */
    fun setCenterpointFromHit(hitPose: Pose): Boolean {
        // Only allow one centerpoint per session
        if (anchor != null) {
            Log.w("ARRenderer", "Centerpoint already set. Only one allowed per session.")
            return false
        }
        
        try {
            anchor = session.createAnchor(hitPose)
            Log.d("ARRenderer", "Centerpoint set from tap at pose: $hitPose")
            return true
        } catch (e: Exception) {
            Log.e("ARRenderer", "Failed to create anchor", e)
            return false
        }
    }
    
    /**
     * Check if centerpoint has been set
     */
    fun isCenterpointSet(): Boolean = anchor != null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        cameraBackgroundRenderer.createOnGlThread(session)
        boundaryRenderer.init()
        planeRenderer.init()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        session.setDisplayGeometry(display.rotation, width, height)
        viewportWidth = width
        viewportHeight = height
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

        // ✅ Render all detected planes as gray meshes ONLY when centerpoint is not set
        // Once centerpoint is set (anchor exists), hide the gray meshes
        if (anchor == null) {
            for (plane in session.getAllTrackables(Plane::class.java)) {
                if (plane.trackingState == TrackingState.TRACKING && 
                    plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING) {
                    planeRenderer.draw(plane, camera)
                }
            }
        }

        // ✅ Check if anchor is still valid
        if (anchor != null && anchor!!.trackingState != TrackingState.TRACKING) {
            Log.d("ARRenderer", "Anchor lost tracking")
            anchor = null
            if (boundaryVisible) {
                boundaryVisible = false
                onBoundaryVisibleChanged?.invoke(false)
            }
        }

        // ✅ Draw boundary only if centerpoint (anchor) is set and tracking
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

    // === Public helpers for detection enrichment ===
    fun getFxPixels(): Float? {
        val camera = lastCamera ?: return null
        return try {
            val intrinsics = camera.imageIntrinsics
            intrinsics.focalLength[0] // fx in pixels
        } catch (_: Exception) { null }
    }

    fun worldPointAndDepthFromScreen(screenX: Float, screenY: Float): Pair<FloatArray, Float>? {
        try {
            val frame = lastFrame ?: return null
            val anchor = anchor ?: return null
            
            // Check if anchor is tracking
            if (anchor.trackingState != TrackingState.TRACKING) {
                Log.w("ARRenderer", "Anchor not tracking")
                return null
            }
            
            val anchorPose = anchor.pose
            val hitList = try { 
                frame.hitTest(screenX, screenY) 
            } catch (e: Exception) { 
                Log.w("ARRenderer", "Hit test failed", e)
                emptyList() 
            }
            
            val best = hitList.firstOrNull { 
                it.trackable is Plane && 
                (it.trackable as Plane).isPoseInPolygon(it.hitPose) 
            } ?: return null
            
            val cameraPose = frame.camera.displayOrientedPose
            val hitPose = best.hitPose
            val dx = hitPose.tx() - cameraPose.tx()
            val dy = hitPose.ty() - cameraPose.ty()
            val dz = hitPose.tz() - cameraPose.tz()
            val depth = kotlin.math.sqrt(dx*dx + dy*dy + dz*dz)
            
            // vector relative to anchor origin in world space
            val anchorInv = FloatArray(16)
            val anchorMatrix = FloatArray(16)
            anchorPose.toMatrix(anchorMatrix, 0)
            android.opengl.Matrix.invertM(anchorInv, 0, anchorMatrix, 0)
            
            val hitMatrix = FloatArray(16)
            hitPose.toMatrix(hitMatrix, 0)
            val local = FloatArray(16)
            android.opengl.Matrix.multiplyMM(local, 0, anchorInv, 0, hitMatrix, 0)
            val localVec = floatArrayOf(local[12], local[13], local[14])
            
            return localVec to depth
        } catch (e: Exception) {
            Log.e("ARRenderer", "Error in worldPointAndDepthFromScreen", e)
            return null
        }
    }

    fun isLocalPointInsideBoundary(localPoint: FloatArray): Boolean {
        // distance in XZ plane from center
        val d = kotlin.math.sqrt(localPoint[0]*localPoint[0] + localPoint[2]*localPoint[2])
        return d <= boundaryRadiusMeters
    }
    
    fun projectLocalPointToScreen(localPoint: FloatArray): Pair<Float, Float>? {
        val frame = lastFrame ?: return null
        val camera = lastCamera ?: return null
        val anchor = anchor ?: return null
        if (viewportWidth <= 0 || viewportHeight <= 0) return null
        if (anchor.trackingState != TrackingState.TRACKING || camera.trackingState != TrackingState.TRACKING) return null

        return try {
            val worldPoint = anchor.pose.transformPoint(localPoint)

            val viewMatrix = FloatArray(16)
            val projectionMatrix = FloatArray(16)
            camera.getViewMatrix(viewMatrix, 0)
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)

            val worldVec = floatArrayOf(worldPoint[0], worldPoint[1], worldPoint[2], 1f)
            val viewVec = FloatArray(4)
            android.opengl.Matrix.multiplyMV(viewVec, 0, viewMatrix, 0, worldVec, 0)

            val clipVec = FloatArray(4)
            android.opengl.Matrix.multiplyMV(clipVec, 0, projectionMatrix, 0, viewVec, 0)
            val w = clipVec[3]
            if (w == 0f) return null

            val ndcX = clipVec[0] / w
            val ndcY = clipVec[1] / w

            val screenX = (ndcX * 0.5f + 0.5f) * viewportWidth
            val screenY = ((-ndcY) * 0.5f + 0.5f) * viewportHeight

            if (screenX.isNaN() || screenY.isNaN()) null else (screenX to screenY)
        } catch (e: Exception) {
            Log.e("ARRenderer", "projectLocalPointToScreen failed", e)
            null
        }
    }
 
    /**
     * Perform hit test on screen coordinates to find tapped plane.
     * Returns the hit pose if a plane was tapped, null otherwise.
     */
    fun hitTestPlane(screenX: Float, screenY: Float): Pose? {
        val frame = lastFrame ?: return null
        val hitList = try { 
            frame.hitTest(screenX, screenY) 
        } catch (e: Exception) { 
            Log.e("ARRenderer", "Hit test failed", e)
            emptyList() 
        }
        
        // Find first hit that is on a horizontal plane
        val planeHit = hitList.firstOrNull { hit ->
            hit.trackable is Plane && 
            (hit.trackable as Plane).type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
            (hit.trackable as Plane).isPoseInPolygon(hit.hitPose)
        }
        
        return planeHit?.hitPose
    }
}
