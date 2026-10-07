package io.asv.mtgocr.ocrreader

import android.opengl.GLES20
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Two thin holographic planes raised above the tracked card. No textures or CPU animation. */
class TripleEnergyBorder {
    private val program: Int
    private val uv=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-.82f,-.82f,.82f,-.82f,-.82f,.82f,.82f,.82f)); position(0)
    }
    private val position: Int
    private val vp: Int
    private val model: Int
    private val extent: Int
    private val time: Int
    private val lift: Int
    private val intensity: Int
    private val smokePass: Int
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
        smokePass=GLES20.glGetUniformLocation(program,"uSmokePass")
    }
    fun draw(viewProjection: FloatArray,pose: FloatArray,width: Float,height: Float,color:Int) {
        GLES20.glUseProgram(program)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(program,"uColor"),((color shr 16) and 255)/255f,((color shr 8) and 255)/255f,(color and 255)/255f)
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
            // Alpha-composited smoke stays visible over a bright camera feed; neon is additive.
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glUniform1f(smokePass,1f)
            GLES20.glUniform1f(lift,.002f); GLES20.glUniform1f(intensity,1f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE)
            GLES20.glUniform1f(smokePass,0f)
            GLES20.glUniform1f(lift,.004f); GLES20.glUniform1f(intensity,1f)
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
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            varying vec2 vUv;
            uniform float uTime;
            uniform float uIntensity;
            uniform float uSmokePass;
            uniform vec3 uColor;
            float hash(vec2 p) {
                // Keep intermediates small: the old sin()*43758 hash lost its fraction on mediump GPUs.
                vec3 q=fract(vec3(p.xyx)*0.1031);
                q+=dot(q,q.yzx+19.19);
                return fract((q.x+q.y)*q.z);
            }
            float noise(vec2 p) {
                vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
                return mix(mix(hash(i),hash(i+vec2(1.0,0.0)),f.x),mix(hash(i+vec2(0.0,1.0)),hash(i+vec2(1.0,1.0)),f.x),f.y);
            }
            float mist(vec2 p) { return 0.57*noise(p)+0.28*noise(p*2.03+7.0)+0.15*noise(p*4.01); }
            void main() {
                vec2 q=abs(vUv-0.5)-vec2(0.485);
                float signedD=length(max(q,0.0))+min(max(q.x,q.y),0.0)-0.015;
                float d=abs(signedD);
                if(uSmokePass>0.5) {
                    vec2 flow=vUv*6.0+vec2(uTime*0.42,-uTime*0.58);
                    float n=mist(flow+vec2(noise(flow-uTime*0.24),noise(flow+4.0))*2.4);
                    float cloud=smoothstep(0.20,0.76,n);
                    float outside=smoothstep(-0.055,-0.012,signedD);
                    float plume=(1.0-smoothstep(0.045,0.29,d))*outside;
                    float alpha=plume*(0.18+0.72*cloud)*uIntensity;
                    gl_FragColor=vec4(uColor*(0.52+0.32*cloud),min(alpha,0.88));
                } else {
                    float core=1.0-smoothstep(0.003,0.012,d);
                    float glow=exp(-d*24.0);
                    float pulse=0.94+0.06*sin(uTime*1.6);
                    vec3 color=mix(uColor,vec3(1.0),core*0.72);
                    gl_FragColor=vec4(color,min((core+glow*0.62)*pulse*uIntensity,1.0));
                }
            }
        """
    }
}
