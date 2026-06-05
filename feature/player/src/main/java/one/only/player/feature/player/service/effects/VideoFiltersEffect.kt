package one.only.player.feature.player.service.effects

import android.content.Context
import android.opengl.GLES20
import android.os.SystemClock
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
internal class VideoFiltersEffect(
    transition: VideoFilterTransition,
    transitionDurationMs: Long,
) : GlEffect {

    private val state = VideoFiltersEffectState(
        initialTransition = transition,
        transitionDurationMs = transitionDurationMs,
    )

    fun updateTransition(transition: VideoFilterTransition) {
        state.updateTransition(transition)
    }

    override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean,
    ): GlShaderProgram = VideoFiltersShaderProgram(
        useHdr = useHdr,
        state = state,
    )

    override fun isNoOp(
        inputWidth: Int,
        inputHeight: Int,
    ): Boolean = false

    private class VideoFiltersShaderProgram(
        useHdr: Boolean,
        private val state: VideoFiltersEffectState,
    ) : BaseGlShaderProgram(useHdr, 1) {

        private val glProgram = createGlProgram()

        init {
            val identityMatrix = GlUtil.create4x4IdentityMatrix()
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                POSITION_COMPONENT_COUNT,
            )
            glProgram.setFloatsUniform("uTransformationMatrix", identityMatrix)
            glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix)
        }

        override fun configure(
            inputWidth: Int,
            inputHeight: Int,
        ): Size {
            glProgram.setFloatsUniform(
                "uTexelSize",
                floatArrayOf(
                    1f / inputWidth,
                    1f / inputHeight,
                ),
            )
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(
            inputTexId: Int,
            presentationTimeUs: Long,
        ) {
            try {
                glProgram.use()
                setFilterUniforms()
                glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)
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

        private fun setFilterUniforms() {
            val filters = state.currentFilters(SystemClock.elapsedRealtime())
            glProgram.setFloatUniform("uBrightness", filters.brightness)
            glProgram.setFloatUniform("uContrast", filters.contrast)
            glProgram.setFloatUniform("uSaturation", filters.saturation)
            glProgram.setFloatUniform("uHue", filters.hue)
            glProgram.setFloatUniform("uGamma", filters.gamma)
            val sharpness = kotlin.math.sqrt(filters.sharpening.coerceAtLeast(0f)) * SHARPNESS_SCALE
            glProgram.setFloatUniform("uSharpness", sharpness)
            val lineDarken = kotlin.math.sqrt(filters.lineDarken.coerceAtLeast(0f)) * LINE_DARKEN_SCALE
            glProgram.setFloatUniform("uLineDarken", lineDarken)
            val lineThin = kotlin.math.sqrt(filters.lineThin.coerceAtLeast(0f)) * LINE_THIN_SCALE
            glProgram.setFloatUniform("uLineThin", lineThin)
        }

        private fun createGlProgram(): GlProgram = try {
            GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        } catch (exception: GlUtil.GlException) {
            throw VideoFrameProcessingException(exception)
        }
    }

    private class VideoFiltersEffectState(
        initialTransition: VideoFilterTransition,
        private val transitionDurationMs: Long,
    ) {
        @Volatile
        private var transition: VideoFilterTransition = initialTransition

        fun updateTransition(transition: VideoFilterTransition) {
            this.transition = transition
        }

        fun currentFilters(currentMs: Long): VideoFilterPreferences = transition.currentFilters(
            currentMs = currentMs,
            durationMs = transitionDurationMs,
        )
    }

    private companion object {
        private const val POSITION_COMPONENT_COUNT = 4
        private const val VERTEX_COUNT = 4
        private const val SHARPNESS_SCALE = 3.0f
        private const val LINE_DARKEN_SCALE = 1.5f
        private const val LINE_THIN_SCALE = 1.5f

        private const val VERTEX_SHADER = """
            #version 100
            attribute vec4 aFramePosition;
            uniform mat4 uTransformationMatrix;
            uniform mat4 uTexTransformationMatrix;
            varying vec2 vTexSamplingCoord;

            void main() {
              gl_Position = uTransformationMatrix * aFramePosition;
              vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5,
                                          aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
              vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 100
            precision highp float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTexelSize;
            uniform float uBrightness;
            uniform float uContrast;
            uniform float uSaturation;
            uniform float uHue;
            uniform float uGamma;
            uniform float uSharpness;
            uniform float uLineDarken;
            uniform float uLineThin;
            varying vec2 vTexSamplingCoord;

            vec3 rotateHue(vec3 color, float hue) {
              float angle = hue * 0.01745329252;
              float sine = sin(angle);
              float cosine = cos(angle);
              vec3 yiq = vec3(
                dot(color, vec3(0.299, 0.587, 0.114)),
                dot(color, vec3(0.596, -0.274, -0.322)),
                dot(color, vec3(0.211, -0.523, 0.312))
              );
              vec2 chroma = vec2(
                yiq.y * cosine - yiq.z * sine,
                yiq.y * sine + yiq.z * cosine
              );
              yiq = vec3(yiq.x, chroma.x, chroma.y);
              return vec3(
                dot(yiq, vec3(1.0, 0.956, 0.621)),
                dot(yiq, vec3(1.0, -0.272, -0.647)),
                dot(yiq, vec3(1.0, -1.106, 1.703))
              );
            }

            vec3 applyColorFilters(vec3 color) {
              float luma;

              if (uBrightness != 0.0) {
                color = clamp(color + vec3(uBrightness), 0.0, 1.0);
              }
              if (uContrast != 0.0) {
                color = clamp((color - vec3(0.5)) * (1.0 + uContrast) + vec3(0.5), 0.0, 1.0);
              }
              if (uHue != 0.0) {
                float hueRadians = uHue * 0.0174533;
                color = clamp(rotateHue(color, hueRadians), 0.0, 1.0);
              }
              if (uSaturation != 0.0) {
                luma = dot(color, vec3(0.299, 0.587, 0.114));
                color = clamp(mix(vec3(luma), color, 1.0 + uSaturation / 100.0), 0.0, 1.0);
              }
              if (uGamma != 1.0) {
                color = clamp(pow(color, vec3(1.0 / max(uGamma, 0.1))), 0.0, 1.0);
              }
              return color;
            }

             float get_luma(vec3 rgb) {
              return dot(rgb, vec3(0.299, 0.587, 0.114));
            }

            void main() {
              vec2 currentCoord = vTexSamplingCoord;

              if (uLineThin > 0.0) {
                 float nw = get_luma(texture2D(uTexSampler, currentCoord + vec2(-uTexelSize.x, -uTexelSize.y)).rgb);
                 float ne = get_luma(texture2D(uTexSampler, currentCoord + vec2(uTexelSize.x, -uTexelSize.y)).rgb);
                 float sw = get_luma(texture2D(uTexSampler, currentCoord + vec2(-uTexelSize.x, uTexelSize.y)).rgb);
                 float se = get_luma(texture2D(uTexSampler, currentCoord + vec2(uTexelSize.x, uTexelSize.y)).rgb);
                 float n = get_luma(texture2D(uTexSampler, currentCoord + vec2(0.0, -uTexelSize.y)).rgb);
                 float s = get_luma(texture2D(uTexSampler, currentCoord + vec2(0.0, uTexelSize.y)).rgb);
                 float e = get_luma(texture2D(uTexSampler, currentCoord + vec2(uTexelSize.x, 0.0)).rgb);
                 float w = get_luma(texture2D(uTexSampler, currentCoord + vec2(-uTexelSize.x, 0.0)).rgb);
                 
                 float sx = (ne + 2.0*e + se) - (nw + 2.0*w + sw);
                 float sy = (sw + 2.0*s + se) - (nw + 2.0*n + ne);
                 vec2 dir = vec2(sx, sy);
                 vec2 dd = (dir / (length(dir) + 0.01)) * vec2(uTexelSize.x, uTexelSize.y) * uLineThin * 1.5;
                 currentCoord += dd;
              }

              vec4 center = texture2D(uTexSampler, currentCoord);
              vec3 sourceColor = center.rgb;

              if (uLineDarken > 0.0 || uSharpness > 0.0) {
                 vec3 c_tl = texture2D(uTexSampler, currentCoord + vec2(-1.0, -1.0) * uTexelSize).rgb;
                 vec3 c_t  = texture2D(uTexSampler, currentCoord + vec2(0.0, -1.0) * uTexelSize).rgb;
                 vec3 c_tr = texture2D(uTexSampler, currentCoord + vec2(1.0, -1.0) * uTexelSize).rgb;
                 vec3 c_l  = texture2D(uTexSampler, currentCoord + vec2(-1.0, 0.0) * uTexelSize).rgb;
                 vec3 c_r  = texture2D(uTexSampler, currentCoord + vec2(1.0, 0.0) * uTexelSize).rgb;
                 vec3 c_bl = texture2D(uTexSampler, currentCoord + vec2(-1.0, 1.0) * uTexelSize).rgb;
                 vec3 c_b  = texture2D(uTexSampler, currentCoord + vec2(0.0, 1.0) * uTexelSize).rgb;
                 vec3 c_br = texture2D(uTexSampler, currentCoord + vec2(1.0, 1.0) * uTexelSize).rgb;

                 float l_c  = get_luma(sourceColor);
                 float l_tl = get_luma(c_tl);
                 float l_t  = get_luma(c_t);
                 float l_tr = get_luma(c_tr);
                 float l_l  = get_luma(c_l);
                 float l_r  = get_luma(c_r);
                 float l_bl = get_luma(c_bl);
                 float l_b  = get_luma(c_b);
                 float l_br = get_luma(c_br);

                 if (uLineDarken > 0.0) {
                    float blurredLuma = (l_t + l_b + l_r + l_l + l_c * 4.0) / 8.0;
                    float diff = min(l_c - blurredLuma, 0.0);
                    sourceColor = clamp(sourceColor + vec3(diff * uLineDarken * 2.0), 0.0, 1.0);
                 }

                 if (uSharpness > 0.0) {
                    vec3 minColor = min(sourceColor, min(min(min(c_tl, c_t), min(c_tr, c_l)), min(min(c_r, c_bl), min(c_b, c_br))));
                    vec3 maxColor = max(sourceColor, max(max(max(c_tl, c_t), max(c_tr, c_l)), max(max(c_r, c_bl), max(c_b, c_br))));
                    
                    // Gaussian 3x3 blur approximation
                    vec3 blurred = (c_tl + c_tr + c_bl + c_br + 2.0 * (c_t + c_l + c_r + c_b) + 4.0 * sourceColor) / 16.0;
                    vec3 diff = sourceColor - blurred;
                    vec3 sharpened = sourceColor + uSharpness * diff;
                    sourceColor = clamp(sharpened, minColor, maxColor);
                 }
              }

              gl_FragColor = vec4(applyColorFilters(sourceColor), center.a);
            }
        """
    }
}
