package com.bakhawone.thesis_bakhawone

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import com.google.ar.core.Frame
import com.google.ar.core.Session
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class CameraBackgroundRenderer {

    private var textureId = -1
    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var textureUniformHandle = 0
    private var texMatrixHandle = 0

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texCoordBuffer: FloatBuffer
    private lateinit var texCoordInput: FloatBuffer
    private lateinit var texCoordOutput: FloatBuffer

    private val texMatrix = FloatArray(16)

    private val quadCoords = floatArrayOf(
        -1f, -1f,
        1f, -1f,
        -1f,  1f,
        1f,  1f
    )

    private val texCoords = floatArrayOf(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f
    )

    fun createOnGlThread(session: Session) {
        // --- Create camera texture ---
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        session.setCameraTextureName(textureId)

        vertexBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(quadCoords); position(0) }

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(texCoords); position(0) }

        texCoordInput = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(texCoords); position(0) }

        texCoordOutput = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        val vertexShaderCode = """
            attribute vec4 vPosition;
            attribute vec2 vTexCoord;
            varying vec2 texCoord;
            uniform mat4 texMatrix;
            void main() {
                gl_Position = vPosition;
                texCoord = (texMatrix * vec4(vTexCoord, 0.0, 1.0)).xy;
            }
        """.trimIndent()

        val fragmentShaderCode = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 texCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, texCoord);
            }
        """.trimIndent()

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
            GLES20.glUseProgram(it)
        }

        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "vTexCoord")
        textureUniformHandle = GLES20.glGetUniformLocation(program, "sTexture")
        texMatrixHandle = GLES20.glGetUniformLocation(program, "texMatrix")
    }

    fun draw(frame: Frame) {
        GLES20.glUseProgram(program)

        frame.transformDisplayUvCoords(texCoordInput, texCoordOutput)
        texCoordOutput.position(0)

        Matrix.setIdentityM(texMatrix, 0)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordOutput)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(textureUniformHandle, 0)
        GLES20.glUniformMatrix4fv(texMatrixHandle, 1, false, texMatrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        return shader
    }
}
