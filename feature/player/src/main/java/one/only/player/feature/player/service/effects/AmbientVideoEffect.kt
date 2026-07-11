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
import one.only.player.core.model.AmbientFrameExtendPreset
import one.only.player.core.model.AmbientGlowPreset
import one.only.player.core.model.AmbientVisualMode

@OptIn(UnstableApi::class)
internal class AmbientVideoEffect(
    private val targetAspectRatio: Float,
    private val mode: AmbientVisualMode,
    private val glowPreset: AmbientGlowPreset,
    private val frameExtendPreset: AmbientFrameExtendPreset,
) : GlEffect {

    override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean,
    ): GlShaderProgram = AmbientShaderProgram(
        useHdr = useHdr,
        targetAspectRatio = targetAspectRatio,
        mode = mode,
        glowPreset = glowPreset,
        frameExtendPreset = frameExtendPreset,
    )

    override fun isNoOp(
        inputWidth: Int,
        inputHeight: Int,
    ): Boolean = false

    private class AmbientShaderProgram(
        useHdr: Boolean,
        private val targetAspectRatio: Float,
        private val mode: AmbientVisualMode,
        private val glowPreset: AmbientGlowPreset,
        private val frameExtendPreset: AmbientFrameExtendPreset,
    ) : BaseGlShaderProgram(useHdr, 1) {

        private var glProgram: GlProgram? = null

        override fun configure(
            inputWidth: Int,
            inputHeight: Int,
        ): Size {
            val inputAspectRatio = inputWidth.toFloat() / inputHeight.toFloat()
            val outputAspectRatio = targetAspectRatio.takeIf { it.isFinite() && it > 0f } ?: inputAspectRatio
            
            val outputWidth: Int
            val outputHeight: Int
            if (outputAspectRatio >= inputAspectRatio) {
                outputHeight = inputHeight
                outputWidth = (inputHeight * outputAspectRatio).toInt()
            } else {
                outputWidth = inputWidth
                outputHeight = (inputWidth / outputAspectRatio).toInt()
            }

            val scaleX = inputWidth.toDouble() / outputWidth.toDouble()
            val scaleY = inputHeight.toDouble() / outputHeight.toDouble()

            if (glProgram == null) {
                glProgram = createGlProgram(scaleX, scaleY)
                val identityMatrix = GlUtil.create4x4IdentityMatrix()
                glProgram?.setBufferAttribute(
                    "aFramePosition",
                    GlUtil.getNormalizedCoordinateBounds(),
                    4,
                )
                glProgram?.setFloatsUniform("uTransformationMatrix", identityMatrix)
                glProgram?.setFloatsUniform("uTexTransformationMatrix", identityMatrix)
            }
            
            glProgram?.setFloatsUniform("HOOKED_size", floatArrayOf(inputWidth.toFloat(), inputHeight.toFloat()))

            return Size(outputWidth, outputHeight)
        }

        override fun drawFrame(
            inputTexId: Int,
            presentationTimeUs: Long,
        ) {
            val program = glProgram ?: return
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                GlUtil.checkGlError()
            } catch (exception: GlUtil.GlException) {
                throw VideoFrameProcessingException(exception, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try {
                glProgram?.delete()
                glProgram = null
            } catch (exception: GlUtil.GlException) {
                throw VideoFrameProcessingException(exception)
            }
        }

        private fun createGlProgram(scaleX: Double, scaleY: Double): GlProgram {
            val context = AmbientRenderContext(scaleX, scaleY)
            val shared = AmbientSharedShaderConfig(0f, 0f, 1f)
            
            val spec = when (mode) {
                AmbientVisualMode.GLOW -> AmbientGlowShaderSpec(
                    context = context,
                    shared = AmbientSharedShaderConfig(0f, glowPreset.vignetteStrength, glowPreset.opacity),
                    blurSamples = glowPreset.blurSamples,
                    maxRadius = glowPreset.maxRadius,
                    glowIntensity = glowPreset.glowIntensity,
                    satBoost = glowPreset.satBoost,
                    warmth = glowPreset.warmth,
                    fadeCurve = glowPreset.fadeCurve
                )
                AmbientVisualMode.FRAME_EXTEND -> AmbientFrameExtendShaderSpec(
                    context = context,
                    shared = AmbientSharedShaderConfig(frameExtendPreset.bezelDepth, frameExtendPreset.vignetteStrength, frameExtendPreset.opacity),
                    sampleBudget = frameExtendPreset.sampleBudget,
                    extendStrength = frameExtendPreset.extendStrength,
                    detailProtection = frameExtendPreset.detailProtection,
                    glowMix = frameExtendPreset.glowMix,
                    ditherNoise = frameExtendPreset.ditherNoise
                )
                AmbientVisualMode.YOUTUBE -> AmbientYouTubeShaderSpec(
                    context = context,
                    shared = shared
                )
            }

            val fragmentShader = AmbientShaderBuilder.build(spec)

            return try {
                GlProgram(VERTEX_SHADER, fragmentShader)
            } catch (exception: GlUtil.GlException) {
                throw VideoFrameProcessingException(exception)
            }
        }

        private companion object {
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
        }
    }
}
