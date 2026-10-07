package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Actual 3D hinged image panels and a rotating faceted energy core, anchored to the card pose. */
class ArCardReveal {
    private fun buffer(v: FloatArray)=ByteBuffer.allocateDirect(v.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(v); position(0) }
    private val panel=buffer(floatArrayOf(0f,0f,0f,1f,0f,0f,0f,1f,0f,1f,1f,0f))
    private val core=buffer(buildList<Float> {
        val ring=arrayOf(floatArrayOf(1f,0f,0f),floatArrayOf(0f,0f,1f),floatArrayOf(-1f,0f,0f),floatArrayOf(0f,0f,-1f))
        for(i in 0..3) for(sign in listOf(1f,-1f))
            for(v in listOf(floatArrayOf(0f,sign,0f),ring[i],ring[(i+1)%4])) addAll(v.toList())
    }.toFloatArray())
    private val program: Int
    private var texture=0
    private val locations: Map<String,Int>
    init {
        fun compile(type: Int,source: String): Int {
            val s=GLES20.glCreateShader(type); GLES20.glShaderSource(s,source); GLES20.glCompileShader(s)
            val ok=IntArray(1); GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0)
            if(ok[0]==0) { val message=GLES20.glGetShaderInfoLog(s); GLES20.glDeleteShader(s); error(message) }
            return s
        }
        val v=compile(GLES20.GL_VERTEX_SHADER,VERTEX)
        val f=try { compile(GLES20.GL_FRAGMENT_SHADER,FRAGMENT) } catch(e: Exception) { GLES20.glDeleteShader(v); throw e }
        program=GLES20.glCreateProgram(); GLES20.glAttachShader(program,v); GLES20.glAttachShader(program,f); GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(v); GLES20.glDeleteShader(f)
        val ok=IntArray(1); GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0)
        if(ok[0]==0) { val message=GLES20.glGetProgramInfoLog(program); GLES20.glDeleteProgram(program); error(message) }
        locations=listOf("uVp","uModel","uExtent","uTime","uSide","uMode","uAngle","uImage").associateWith { GLES20.glGetUniformLocation(program,it) }
    }
    fun upload(bitmap: Bitmap) {
        if(texture==0) { val ids=IntArray(1); GLES20.glGenTextures(1,ids,0); texture=ids[0] }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0)
    }
    fun draw(vp: FloatArray,pose: FloatArray,width: Float,height: Float,elapsed: Float) {
        if(texture==0) return
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(locations.getValue("uVp"),1,false,vp,0)
        GLES20.glUniformMatrix4fv(locations.getValue("uModel"),1,false,pose,0)
        GLES20.glUniform2f(locations.getValue("uExtent"),width,height)
        GLES20.glUniform1f(locations.getValue("uTime"),elapsed%60f)
        val t=((elapsed-.3f)/1.4f).coerceIn(0f,1f)
        GLES20.glUniform1f(locations.getValue("uAngle"),t*t*(3-2*t)*1.92f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
        GLES20.glUniform1i(locations.getValue("uImage"),0)
        val position=GLES20.glGetAttribLocation(program,"aPosition")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glDepthMask(true)
        try {
            panel.position(0); GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,0,panel)
            GLES20.glUniform1f(locations.getValue("uMode"),0f)
            for(side in floatArrayOf(-1f,1f)) {
                GLES20.glUniform1f(locations.getValue("uSide"),side)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
            }
            if(t>.1f) {
                core.position(0); GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,0,core)
                GLES20.glUniform1f(locations.getValue("uMode"),1f)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,24)
            }
        } finally { GLES20.glDisableVertexAttribArray(position); GLES20.glDisable(GLES20.GL_DEPTH_TEST) }
    }
    fun close() { if(texture!=0) GLES20.glDeleteTextures(1,intArrayOf(texture),0); GLES20.glDeleteProgram(program) }
    companion object {
        private const val VERTEX="""
            attribute vec3 aPosition;
            uniform mat4 uVp; uniform mat4 uModel; uniform vec2 uExtent;
            uniform float uSide; uniform float uMode; uniform float uAngle; uniform float uTime;
            varying vec2 vUv; varying vec3 vLocal;
            void main() {
                vec3 p;
                if(uMode<0.5) {
                    float hinge=uSide*uExtent.x*0.5;
                    float arm=aPosition.x*uExtent.x*0.5;
                    p=vec3(hinge-uSide*arm*cos(uAngle),0.002+arm*sin(uAngle),(aPosition.y-0.5)*uExtent.y);
                    vUv=vec2(uSide<0.0?aPosition.x*0.5:1.0-aPosition.x*0.5,aPosition.y);
                } else {
                    float c=cos(uTime*1.1); float s=sin(uTime*1.1);
                    p=vec3(c*aPosition.x+s*aPosition.z,aPosition.y,-s*aPosition.x+c*aPosition.z);
                    p*=uExtent.x*(0.19+0.015*sin(uTime*3.0));
                    p.y+=0.012+0.004*sin(uTime*1.5);
                    vUv=aPosition.xz;
                }
                vLocal=aPosition;
                gl_Position=uVp*uModel*vec4(p,1.0);
            }
        """
        private const val FRAGMENT="""
            precision mediump float;
            uniform sampler2D uImage; uniform float uMode; uniform float uTime;
            varying vec2 vUv; varying vec3 vLocal;
            void main() {
                if(uMode<0.5) {
                    vec4 image=texture2D(uImage,vUv);
                    float rim=1.0-smoothstep(0.0,0.018,min(min(vUv.x,1.0-vUv.x),min(vUv.y,1.0-vUv.y)));
                    gl_FragColor=vec4(mix(image.rgb,vec3(0.2,0.9,1.0),rim*0.8),1.0);
                } else {
                    float streak=pow(0.5+0.5*sin(vLocal.y*28.0+uTime*4.0),6.0);
                    vec3 color=mix(vec3(0.15,0.08,0.65),vec3(0.05,0.95,1.0),vLocal.y*0.5+0.5);
                    gl_FragColor=vec4(color+streak*0.55,1.0);
                }
            }
        """
    }
}
