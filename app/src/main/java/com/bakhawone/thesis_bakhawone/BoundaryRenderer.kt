package com.bakhawone.thesis_bakhawone

import android.opengl.GLES20
import com.google.ar.core.Pose
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class BoundaryRenderer {

    private lateinit var vertexBuffer: FloatBuffer
    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpMatrixHandle = 0
    private var vertexCount = 0

    private val colorBlue = floatArrayOf(0f, 0f, 1f, 1f) // RGBA Blue

    fun init() {
        // Precompute circle points (12.6m radius for ~500 sqm)
        val radius = sqrt(3f / PI.toFloat()) // Float radius
        val segments = 60
        val circleVertices = FloatArray(segments * 3)

        for (i in 0 until segments) {
            val angle = (2f * PI.toFloat() * i) / segments
            val x = radius * cos(angle)
            val z = radius * sin(angle)
            circleVertices[i * 3] = x.toFloat()
            circleVertices[i * 3 + 1] = 0f
            circleVertices[i * 3 + 2] = z.toFloat()
        }

        vertexCount = circleVertices.size / 3

        val bb = ByteBuffer.allocateDirect(circleVertices.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(circleVertices)
        vertexBuffer.position(0)

        // Vertex shader
        val vertexShaderCode = """
            uniform mat4 uMVPMatrix;
            attribute vec4 vPosition;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
            }
        """.trimIndent()

        // Fragment shader
        val fragmentShaderCode = """
            precision mediump float;
            uniform vec4 vColor;
            void main() {
                gl_FragColor = vColor;
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
        colorHandle = GLES20.glGetUniformLocation(program, "vColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
    }

    fun draw(centerPose: Pose) {
        GLES20.glUseProgram(program)

        // Prepare position data
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle,
            3,
            GLES20.GL_FLOAT,
            false,
            3 * 4,
            vertexBuffer
        )

        // Apply color
        GLES20.glUniform4fv(colorHandle, 1, colorBlue, 0)

        // Set MVP matrix (identity, since ARCore handles world pose)
        val identityMatrix = FloatArray(16) { if (it % 5 == 0) 1f else 0f }
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, identityMatrix, 0)

        // Draw circle as loop
        GLES20.glLineWidth(4f)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
