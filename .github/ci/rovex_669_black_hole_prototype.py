from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
pkg=root/'app/src/main/java/com/localqbank/library'
gradle=root/'app/build.gradle.kts'
g=gradle.read_text()
if 'versionName = "8.3.667"' not in g or 'versionCode = 753' not in g:
    raise SystemExit('[669] wrong baseline')
src = r'''package com.localqbank.library
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.View
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Isolated, opt-in GPU black-hole prototype. Not yet wired into Splash or Home.
 * Uses a single full-screen triangle and a bounded shader. Not a scientific simulation.
 */
class RovexBlackHolePreviewView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : GLSurfaceView(c,a) {
    private val scene = Scene()
    private var active = false
    private val tick = object : Runnable {
        override fun run() {
            if (!active || !isShown || windowVisibility != View.VISIBLE) return
            requestRender()
            postDelayed(this, 42L)
        }
    }
    init {
        setEGLContextClientVersion(2)
        setRenderer(scene)
        renderMode = RENDERMODE_WHEN_DIRTY
        isClickable = false
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); setActive(isShown && windowVisibility == View.VISIBLE) }
    override fun onDetachedFromWindow() { setActive(false); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(v: View, visibility: Int) {
        super.onVisibilityChanged(v, visibility)
        setActive(isShown && visibility == View.VISIBLE && windowVisibility == View.VISIBLE)
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        setActive(isShown && visibility == View.VISIBLE)
    }
    override fun onPause() { setActive(false); super.onPause() }
    override fun onResume() { super.onResume(); setActive(isShown && windowVisibility == View.VISIBLE) }
    private fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        removeCallbacks(tick)
        if (active) post(tick)
    }
    private class Scene : Renderer {
        private var program=0
        private var resolution=-1
        private var time=-1
        private var width=1
        private var height=1
        private var start=0L
        private val buffer=java.nio.ByteBuffer.allocateDirect(24).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f,-1f,3f,-1f,-1f,3f)); position(0)
        }
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            val vs=shader(GLES20.GL_VERTEX_SHADER, VERT)
            val fs=shader(GLES20.GL_FRAGMENT_SHADER, FRAG)
            program=GLES20.glCreateProgram()
            GLES20.glAttachShader(program,vs); GLES20.glAttachShader(program,fs); GLES20.glLinkProgram(program)
            val ok=IntArray(1); GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0)
            if(ok[0]==0) throw IllegalStateException("Black-hole shader link: "+GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
            resolution=GLES20.glGetUniformLocation(program,"uResolution")
            time=GLES20.glGetUniformLocation(program,"uTime")
            start=System.nanoTime()
        }
        override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { width=w; height=h.coerceAtLeast(1); GLES20.glViewport(0,0,w,h) }
        override fun onDrawFrame(gl: GL10?) {
            if(program==0) { GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT); return }
            GLES20.glUseProgram(program)
            GLES20.glUniform2f(resolution,width.toFloat(),height.toFloat())
            GLES20.glUniform1f(time,(System.nanoTime()-start)/1.0e9f)
            val p=GLES20.glGetAttribLocation(program,"aPosition")
            GLES20.glEnableVertexAttribArray(p); GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,0,buffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,3); GLES20.glDisableVertexAttribArray(p)
        }
        private fun shader(type:Int, source:String):Int {
            val id=GLES20.glCreateShader(type); GLES20.glShaderSource(id,source); GLES20.glCompileShader(id)
            val ok=IntArray(1); GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,ok,0)
            if(ok[0]==0) throw IllegalStateException("Black-hole shader compile: "+GLES20.glGetShaderInfoLog(id))
            return id
        }
        companion object {
            private const val VERT = "attribute vec2 aPosition; void main(){gl_Position=vec4(aPosition,0.0,1.0);}"
            private const val FRAG = """
                precision highp float;
                uniform vec2 uResolution;
                uniform float uTime;
                float h(vec2 p){p=fract(p*vec2(123.34,456.21));p+=dot(p,p+45.32);return fract(p.x*p.y);}
                float n(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(h(i),h(i+vec2(1.,0.)),f.x),mix(h(i+vec2(0.,1.)),h(i+vec2(1.,1.)),f.x),f.y);}
                float fb(vec2 p){float v=0.,a=.5;for(int i=0;i<4;i++){v+=a*n(p);p=mat2(1.6,-1.2,1.2,1.6)*p;a*=.5;}return v;}
                void main(){
                  vec2 p=(gl_FragCoord.xy-.5*uResolution.xy)/uResolution.y;
                  float r=length(p),a=atan(p.y,p.x),t=uTime*.045;
                  vec3 c=vec3(.0015,.003,.008);
                  vec2 cell=floor(p*110.);float s=h(cell);
                  c+=smoothstep(.994,.999,s)*(0.45+0.55*sin(t*9.+s*80.))*mix(vec3(.22,.42,.8),vec3(1.,.72,.43),h(cell+13.));
                  float dust=fb(p*5.+vec2(t,-t*.6));
                  float band=exp(-abs(p.y+.12*sin(a*2.))*5.);
                  c+=band*(.02+dust*.045)*vec3(.18,.28,.65);
                  c+=band*fb(p*11.-t)*.022*vec3(.55,.16,.45);
                  float bend=.0018/max(r,.055);
                  vec2 q=p+normalize(p+vec2(.0001))*bend;
                  float qr=length(q),qa=atan(q.y,q.x);
                  float y=abs(q.y+.016*sin(qa*3.+t));
                  float disk=exp(-y/(.014+.01*n(vec2(qa*5.,qr*8.))))*smoothstep(.145,.205,qr)*(1.-smoothstep(.29,.48,qr));
                  float turbulence=fb(vec2(qa*3.+t*2.,qr*24.-t*3.));
                  float threads=pow(max(0.,sin(qa*18.+t*4.+turbulence*5.)),7.);
                  float heat=clamp(1.1-qr*2.2+turbulence*.65,0.,1.);
                  vec3 plasma=mix(vec3(.18,.24,.78),vec3(1.,.25,.035),heat);
                  plasma=mix(plasma,vec3(1.,.86,.62),pow(heat,5.));
                  c+=disk*(.55+turbulence*1.35+threads*.65)*plasma;
                  c+=exp(-y*13.)*exp(-abs(qr-.29)*8.)*vec3(1.,.28,.07)*.18;
                  float ring=exp(-pow((r-.212)/.005,2.));
                  c+=ring*vec3(1.,.68,.34)*.8;
                  c*=smoothstep(.205,.211,r);
                  c=c/(1.+c*.65);c=pow(max(c,vec3(0.)),vec3(.88));
                  gl_FragColor=vec4(c,1.);
                }
            """
        }
    }
}
'''
path=pkg/'RovexBlackHolePreviewView.kt'
if path.exists(): raise SystemExit('[669] prototype already exists')
path.write_text(src)
g=g.replace('versionName = "8.3.667"','versionName = "8.3.668"',1).replace('versionCode = 753','versionCode = 754',1)
gradle.write_text(g)
print('[669] PASS: isolated GLSurfaceView prototype added, frame capped near 24fps, visibility gated; v8.3.668/code 754')
