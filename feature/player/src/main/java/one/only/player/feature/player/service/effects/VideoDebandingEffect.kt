package one.only.player.feature.player.service.effects

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

@OptIn(UnstableApi::class)
internal class VideoDebandingEffect(
    private var iterations: Int = 1,
    private var threshold: Float = 48.0f,
    private var range: Float = 16.0f,
    private var grain: Float = 32.0f,
) : GlEffect {

    fun updateSettings(iterations: Int, threshold: Float, range: Float, grain: Float) {
        this.iterations = iterations
        this.threshold = threshold
        this.range = range
        this.grain = grain
    }

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = VideoDebandingShaderProgram(useHdr)

    private inner class VideoDebandingShaderProgram(useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
        private val glProgram = createGlProgram()
        private var timeCounter = 0.0f

        init {
            val identityMatrix = GlUtil.create4x4IdentityMatrix()
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
            )
            glProgram.setFloatsUniform("uTransformationMatrix", identityMatrix)
            glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix)
        }

        private fun createGlProgram(): GlProgram {
            val vertexShader = """
                #version 300 es
                in vec4 aFramePosition;
                uniform mat4 uTransformationMatrix;
                uniform mat4 uTexTransformationMatrix;
                out vec2 vTexSamplingCoord;
                void main() {
                  gl_Position = uTransformationMatrix * aFramePosition;
                  vec4 texPosition = vec4(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
                  vTexSamplingCoord = (uTexTransformationMatrix * texPosition).xy;
                }
            """.trimIndent()

            val fragmentShader = """
                #version 300 es
                precision mediump float;
                uniform sampler2D uTexSampler;
                uniform vec2 uTexelSize;
                uniform float uRandom;
                
                uniform int uIterations;
                uniform float uThreshold;
                uniform float uRange;
                uniform float uGrain;
                
                in vec2 vTexSamplingCoord;
                out vec4 outColor;
                
                mediump float mod289(mediump float x) { 
                    return x - floor(x * (1.0/289.0)) * 289.0; 
                }
                mediump float permute(mediump float x) {
                    return mod289(mod289(34.0 * x + 1.0) * (fract(x) + 1.0));
                }
                mediump float rand(mediump float x) { 
                    return fract(x * (1.0/41.0)); 
                }
                
                vec4 HOOKED_texOff(vec2 offset) {
                    return texture(uTexSampler, vTexSamplingCoord + offset * uTexelSize);
                }
                
                mediump vec4 average(mediump float range_val, inout mediump float h) {
                    mediump float dist = rand(h) * range_val;     h = permute(h);
                    mediump float dir  = rand(h) * 6.2831853; h = permute(h);
                    mediump vec2 o = dist * vec2(cos(dir), sin(dir));
                    
                    mediump vec4 ref0 = HOOKED_texOff(vec2( o.x,  o.y));
                    mediump vec4 ref1 = HOOKED_texOff(vec2(-o.y,  o.x));
                    mediump vec4 ref2 = HOOKED_texOff(vec2(-o.x, -o.y));
                    mediump vec4 ref3 = HOOKED_texOff(vec2( o.y, -o.x));
                    
                    return (ref0 + ref1 + ref2 + ref3) * 0.25;
                }
                
                void main() {
                    mediump vec3 _m = vec3(vTexSamplingCoord, uRandom) + vec3(1.0);
                    mediump float h = permute(permute(permute(_m.x) + _m.y) + _m.z);
                    
                    vec4 color = texture(uTexSampler, vTexSamplingCoord);
                    mediump vec4 avg, diff;
                    
                    for (int i = 1; i <= uIterations; i++) {
                        avg = average(float(i) * uRange, h);
                        diff = abs(color - avg);
                        bvec4 cmp = greaterThan(diff, vec4(uThreshold / (float(i) * 16384.0)));
                        color = mix(avg, color, vec4(cmp));
                    }
                    
                    mediump vec3 noise;
                    noise.x = rand(h); h = permute(h);
                    noise.y = rand(h); h = permute(h);
                    noise.z = rand(h); h = permute(h);
                    
                    float gain = uGrain / 8192.0;
                    color.rgb += gain * (noise - vec3(0.5));
                    
                    outColor = color;
                }
            """.trimIndent()

            return try {
                GlProgram(vertexShader, fragmentShader)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            glProgram.setFloatsUniform("uTexelSize", floatArrayOf(1f / inputWidth, 1f / inputHeight))
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                glProgram.use()

                timeCounter += 0.016f
                if (timeCounter > 1000f) timeCounter = 0f
                glProgram.setFloatUniform("uRandom", timeCounter)

                glProgram.setIntUniform("uIterations", iterations)
                glProgram.setFloatUniform("uThreshold", threshold)
                glProgram.setFloatUniform("uRange", range)
                glProgram.setFloatUniform("uGrain", grain)

                glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4) // 4 for TRIANGLE_STRIP instead of VERTEX_COUNT since it's just a quad
                GlUtil.checkGlError()
            } catch (exception: GlUtil.GlException) {
                throw VideoFrameProcessingException(exception, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try {
                glProgram.delete()
            } catch (exception: GlUtil.GlException) {
                throw VideoFrameProcessingException(exception)
            }
        }
    }
}
