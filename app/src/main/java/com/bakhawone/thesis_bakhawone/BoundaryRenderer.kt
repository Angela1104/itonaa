package com.bakhawone.thesis_bakhawone

import android.opengl.GLES20
import android.opengl.Matrix
import com.google.ar.core.Camera
import com.google.ar.core.Pose
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class BoundaryRenderer {

    private var program = 0
    private var positionHandle = 0
    private var mvpMatrixHandle = 0
    private var colorHandle = 0

    private lateinit var circleBuffer: FloatBuffer
    private lateinit var centerBuffer: FloatBuffer

    private val mvpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)

    // ✅ Boundary size — 1000 sqm area radius
    private val radius = 17.841f // 1000 square meters radius
    private val circleCoords = generateCircleVertices(radius, 128)

    // ✅ Center marker — small disk (~0.05 m radius)
    private val centerCoords = generateCircleVertices(0.05f, 32)

    fun init() {
        // Circle buffer
        circleBuffer = ByteBuffer.allocateDirect(circleCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(circleCoords)
                position(0)
            }

        // Center marker buffer
        centerBuffer = ByteBuffer.allocateDirect(centerCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(centerCoords)
                position(0)
            }

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

    fun draw(pose: Pose, camera: Camera?) {
        GLES20.glUseProgram(program)

        // Set up matrices
        pose.toMatrix(modelMatrix, 0)
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

        // Enable depth testing for proper 3D rendering
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        // Draw boundary circle outline
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, circleBuffer)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUniform4f(colorHandle, 0f, 1f, 0f, 0.8f) // green with transparency
        GLES20.glLineWidth(6f)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 1, (circleCoords.size / 3) - 1)
        GLES20.glDisable(GLES20.GL_BLEND)

        // Draw center marker
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, centerBuffer)
        GLES20.glUniform4f(colorHandle, 1f, 1f, 1f, 1f) // white center
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, centerCoords.size / 3)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
    }

    private fun generateCircleVertices(radius: Float, segments: Int): FloatArray {
        val coords = FloatArray((segments + 2) * 3)
        var i = 0
        coords[i++] = 0f; coords[i++] = 0f; coords[i++] = 0f // center
        val step = (2 * PI / segments).toFloat()
        for (angle in 0..segments) {
            val theta = angle * step
            coords[i++] = (radius * cos(theta)).toFloat()
            coords[i++] = 0f
            coords[i++] = (radius * sin(theta)).toFloat()
        }
        return coords
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        return shader
    }
}
