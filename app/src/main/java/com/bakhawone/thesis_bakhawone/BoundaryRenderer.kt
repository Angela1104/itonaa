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

    private lateinit var boundaryBuffer: FloatBuffer
    private lateinit var centerBuffer: FloatBuffer
    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpMatrixHandle = 0
    private var boundaryVertexCount = 0
    private var centerVertexCount = 0

    private val boundaryColor = floatArrayOf(0f, 0f, 1f, 1f)
    private val centerColor = floatArrayOf(1f, 1f, 1f, 1f)

    fun init() {
        val boundaryRadius = sqrt(5f / PI.toFloat())
        val segments = 60
        val boundaryVertices = FloatArray(segments * 3)
        for (i in 0 until segments) {
            val angle = (2f * PI.toFloat() * i) / segments
            boundaryVertices[i * 3] = boundaryRadius * cos(angle)
            boundaryVertices[i * 3 + 1] = 0.15f
            boundaryVertices[i * 3 + 2] = boundaryRadius * sin(angle)
        }
        boundaryVertexCount = boundaryVertices.size / 3
        val bb = ByteBuffer.allocateDirect(boundaryVertices.size * 4)
        bb.order(ByteOrder.nativeOrder())
        boundaryBuffer = bb.asFloatBuffer()
        boundaryBuffer.put(boundaryVertices)
        boundaryBuffer.position(0)

        val centerRadius = 0.2f
        val centerVertices = FloatArray(segments * 3)
        for (i in 0 until segments) {
            val angle = (2f * PI.toFloat() * i) / segments
            centerVertices[i * 3] = centerRadius * cos(angle)
            centerVertices[i * 3 + 1] = 0.15f
            centerVertices[i * 3 + 2] = centerRadius * sin(angle)
        }
        centerVertexCount = centerVertices.size / 3
        val cb = ByteBuffer.allocateDirect(centerVertices.size * 4)
        cb.order(ByteOrder.nativeOrder())
        centerBuffer = cb.asFloatBuffer()
        centerBuffer.put(centerVertices)
        centerBuffer.position(0)

        val vertexShaderCode = """
            uniform mat4 uMVPMatrix;
            attribute vec4 vPosition;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
            }
        """.trimIndent()

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

    fun draw(anchorPose: Pose) {
        GLES20.glUseProgram(program)
        val identityMatrix = FloatArray(16) { if (it % 5 == 0) 1f else 0f }
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, identityMatrix, 0)

        GLES20.glUniform4fv(colorHandle, 1, boundaryColor, 0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, boundaryBuffer)
        GLES20.glLineWidth(4f)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, boundaryVertexCount)
        GLES20.glDisableVertexAttribArray(positionHandle)

        GLES20.glUniform4fv(colorHandle, 1, centerColor, 0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, centerBuffer)
        GLES20.glLineWidth(6f)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, centerVertexCount)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
