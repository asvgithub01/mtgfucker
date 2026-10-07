package io.asv.mtgocr.ocrreader

import android.opengl.GLES20 as GL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A single 96-particle burst, in metres relative to the tracked card. GL-thread only. */
class TripleConfetti {
    private val vertices=ByteBuffer.allocateDirect(96*6*3*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        val corners=floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,-1f,1f,1f,-1f,1f,1f)
        for(i in 0 until 96) for(j in 0 until 6) { put(i.toFloat());put(corners[j*2]);put(corners[j*2+1]) };position(0)
    }
    private fun shader(type:Int,source:String):Int {
        val s=GL.glCreateShader(type);GL.glShaderSource(s,source);GL.glCompileShader(s)
        val ok=IntArray(1);GL.glGetShaderiv(s,GL.GL_COMPILE_STATUS,ok,0)
        if(ok[0]==0) { val message=GL.glGetShaderInfoLog(s);GL.glDeleteShader(s);error(message) };return s
    }
    private val program:Int
    private val position:Int
    private val vp:Int
    private val pose:Int
    private val time:Int
    init {
        val v=shader(GL.GL_VERTEX_SHADER,"""
            attribute vec3 aParticle; uniform mat4 uVp; uniform mat4 uPose; uniform float uTime;
            varying vec4 vColor;
            float random(float x) { return fract(sin(x*127.1+311.7)*43758.5453); }
            void main() {
                float id=aParticle.x; float angle=random(id)*6.283185;
                float speed=0.035+random(id+100.0)*0.075;
                float t=uTime; float spin=t*(4.0+random(id+200.0)*10.0);
                vec3 center=vec3(cos(angle)*speed*t,0.003+(0.12+random(id+300.0)*0.13)*t-0.095*t*t,sin(angle)*speed*t);
                vec2 corner=aParticle.yz*0.0025;
                vec3 p=center+vec3(corner.x*cos(spin),corner.y,corner.x*sin(spin));
                vColor=vec4(0.3+0.7*random(id+400.0),0.3+0.7*random(id+500.0),0.3+0.7*random(id+600.0),max(0.0,1.0-t/3.0));
                gl_Position=uVp*uPose*vec4(p,1.0);
            }
        """)
        val f=try { shader(GL.GL_FRAGMENT_SHADER,"precision mediump float; varying vec4 vColor; void main(){gl_FragColor=vColor;}") } catch(e:Exception) { GL.glDeleteShader(v);throw e }
        program=GL.glCreateProgram();GL.glAttachShader(program,v);GL.glAttachShader(program,f);GL.glLinkProgram(program)
        GL.glDeleteShader(v);GL.glDeleteShader(f)
        val ok=IntArray(1);GL.glGetProgramiv(program,GL.GL_LINK_STATUS,ok,0)
        if(ok[0]==0) { val message=GL.glGetProgramInfoLog(program);GL.glDeleteProgram(program);error(message) }
        position=GL.glGetAttribLocation(program,"aParticle");vp=GL.glGetUniformLocation(program,"uVp");pose=GL.glGetUniformLocation(program,"uPose");time=GL.glGetUniformLocation(program,"uTime")
    }
    fun draw(viewProjection:FloatArray,model:FloatArray,elapsed:Float) {
        GL.glUseProgram(program);GL.glUniformMatrix4fv(vp,1,false,viewProjection,0);GL.glUniformMatrix4fv(pose,1,false,model,0);GL.glUniform1f(time,elapsed)
        GL.glEnable(GL.GL_BLEND);GL.glBlendFunc(GL.GL_SRC_ALPHA,GL.GL_ONE_MINUS_SRC_ALPHA)
        GL.glEnable(GL.GL_DEPTH_TEST);GL.glDepthMask(false);GL.glEnableVertexAttribArray(position)
        try { vertices.position(0);GL.glVertexAttribPointer(position,3,GL.GL_FLOAT,false,0,vertices);GL.glDrawArrays(GL.GL_TRIANGLES,0,96*6) }
        finally { GL.glDisableVertexAttribArray(position);GL.glDepthMask(true);GL.glDisable(GL.GL_DEPTH_TEST);GL.glDisable(GL.GL_BLEND) }
    }
    fun close()=GL.glDeleteProgram(program)
}
