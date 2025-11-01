package com.bakhawone.thesis_bakhawone

import android.opengl.GLES20
import android.opengl.Matrix
import com.google.ar.core.Camera
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class PlaneRenderer {
    
    private var program = 0
    private var positionHandle = 0
    private var mvpMatrixHandle = 0
    private var colorHandle = 0
    
    private val mvpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    
    fun init() {
        val vertexShaderCode = """
            uniform mat4 uMVPMatrix;
            attribute vec4 vPosition;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
            }
        """.trimIndent()
        
        val fragmentShaderCode = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """.trimIndent()
        
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
        
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
    }
    
    fun draw(plane: Plane, camera: Camera?) {
        if (plane.trackingState != com.google.ar.core.TrackingState.TRACKING) return
        
        GLES20.glUseProgram(program)
        
        // Get plane polygon (in local coordinates relative to centerPose)
        // polygon is a FloatBuffer containing x, z pairs
        val polygon = plane.polygon
        if (polygon == null || polygon.remaining() < 6) return // Need at least 3 points (x, z pairs)
        
        // Create a copy of the polygon buffer to work with
        val polygonArray = FloatArray(polygon.remaining())
        val originalPosition = polygon.position()
        polygon.rewind()
        polygon.get(polygonArray)
        polygon.position(originalPosition)
        
        // Polygon contains x, z pairs (numPoints * 2 floats)
        val numPoints = polygonArray.size / 2
        if (numPoints < 3) return
        
        // Create vertex buffer from polygon
        // Polygon is in plane's local coordinates (x, z pairs), y is always 0
        val vertexBuffer = ByteBuffer.allocateDirect(numPoints * 3 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        
        val planePose = plane.centerPose
        for (i in 0 until numPoints) {
            val x = polygonArray[i * 2]
            val z = polygonArray[i * 2 + 1]
            val y = 0f // Plane is horizontal, so y is always 0 in local coordinates
            vertexBuffer.put(floatArrayOf(x, y, z))
        }
        vertexBuffer.position(0)
        
        // Set up matrices
        planePose.toMatrix(modelMatrix, 0)
        if (camera != null) {
            camera.getViewMatrix(viewMatrix, 0)
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)
        } else {
            Matrix.setIdentityM(viewMatrix, 0)
            Matrix.setIdentityM(projectionMatrix, 0)
        }
        
        // Calculate MVP matrix: Projection * View * Model
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)
        
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        
        // Enable depth testing
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        
        // Draw plane mesh in gray (filled)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUniform4f(colorHandle, 0.5f, 0.5f, 0.5f, 0.3f) // Gray with transparency
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, numPoints)
        
        // Draw plane outline
        GLES20.glUniform4f(colorHandle, 0.7f, 0.7f, 0.7f, 0.8f) // Lighter gray for outline
        GLES20.glLineWidth(2f)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, numPoints)
        
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }
    
    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        return shader
    }
}

