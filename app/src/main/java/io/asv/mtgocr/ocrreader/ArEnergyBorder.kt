package io.asv.mtgocr.ocrreader

import android.opengl.GLES20
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Two thin holographic planes raised above the tracked card. No textures or CPU animation. */
class ArEnergyBorder {
    private val program: Int
    private val uv=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-.59f,-.59f,.59f,-.59f,-.59f,.59f,.59f,.59f)); position(0)
    }
    private val position: Int
    private val vp: Int
    private val model: Int
    private val extent: Int
    private val time: Int
    private val lift: Int
    private val intensity: Int
    init {
        val vertex=compile(GLES20.GL_VERTEX_SHADER,VERTEX)
        val fragment=try { compile(GLES20.GL_FRAGMENT_SHADER,FRAGMENT) }
            catch(e: Exception) { GLES20.glDeleteShader(vertex); throw e }
        program=GLES20.glCreateProgram()
        GLES20.glAttachShader(program,vertex); GLES20.glAttachShader(program,fragment)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
        val ok=IntArray(1); GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0)
        if(ok[0]==0) {
            val message=GLES20.glGetProgramInfoLog(program); GLES20.glDeleteProgram(program)
            error(message)
        }
        position=GLES20.glGetAttribLocation(program,"aUv")
        vp=GLES20.glGetUniformLocation(program,"uVp")
        model=GLES20.glGetUniformLocation(program,"uModel")
        extent=GLES20.glGetUniformLocation(program,"uExtent")
        time=GLES20.glGetUniformLocation(program,"uTime")
        lift=GLES20.glGetUniformLocation(program,"uLift")
        intensity=GLES20.glGetUniformLocation(program,"uIntensity")
    }
    fun draw(viewProjection: FloatArray,pose: FloatArray,width: Float,height: Float) {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(vp,1,false,viewProjection,0)
        GLES20.glUniformMatrix4fv(model,1,false,pose,0)
        GLES20.glUniform2f(extent,width,height)
        GLES20.glUniform1f(time,(SystemClock.uptimeMillis()%60_000)/1000f)
        uv.position(0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,0,uv)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE)
        try {
            // Reflection on the card, then a raised rim (3 mm) with perspective/parallax.
            GLES20.glUniform1f(lift,.0003f); GLES20.glUniform1f(intensity,.38f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
            GLES20.glUniform1f(lift,.003f); GLES20.glUniform1f(intensity,1f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
        } finally {
            GLES20.glDisableVertexAttribArray(position)
            GLES20.glDisable(GLES20.GL_BLEND)
        }
    }
    fun close() { GLES20.glDeleteProgram(program) }
    private fun compile(type: Int,source: String): Int {
        val shader=GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader,source); GLES20.glCompileShader(shader)
        val ok=IntArray(1); GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0)
        if(ok[0]==0) {
            val message=GLES20.glGetShaderInfoLog(shader); GLES20.glDeleteShader(shader)
            error(message)
        }
        return shader
    }
    companion object {
        private const val VERTEX="""
            attribute vec2 aUv;
            uniform mat4 uVp;
            uniform mat4 uModel;
            uniform vec2 uExtent;
            uniform float uLift;
            varying vec2 vUv;
            void main() {
                vUv=aUv+0.5;
                gl_Position=uVp*uModel*vec4(aUv.x*uExtent.x,uLift,aUv.y*uExtent.y,1.0);
            }
        """
        private const val FRAGMENT="""
            precision mediump float;
            varying vec2 vUv;
            uniform float uTime;
            uniform float uIntensity;
            void main() {
                vec2 q=abs(vUv-0.5)-0.5;
                float d=abs(max(q.x,q.y));
                float edge=1.0-smoothstep(0.002,0.010,d);
                float halo=1.0-smoothstep(0.004,0.070,d);
                float ribbon=0.5+0.5*sin(vUv.x*21.0+vUv.y*29.0-uTime*3.0);
                float spark=pow(max(0.0,sin(vUv.x*37.0-vUv.y*41.0+uTime*4.0)),18.0);
                float pulse=0.78+0.22*sin(uTime*2.2);
                vec3 color=mix(vec3(0.05,0.8,1.0),vec3(0.7,0.15,1.0),ribbon);
                color=mix(color,vec3(0.8,1.0,1.0),spark*edge);
                float alpha=(edge*(0.6+spark*0.4)+halo*halo*0.3)*pulse*uIntensity;
                gl_FragColor=vec4(color,alpha);
            }
        """
    }
}
