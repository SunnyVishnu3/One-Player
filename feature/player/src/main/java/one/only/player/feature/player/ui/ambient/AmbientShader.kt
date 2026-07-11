package one.only.player.feature.player.ui.ambient

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import one.only.player.core.model.AmbientMode
import one.only.player.core.model.AmbientQuality

// 氛围背景着色器：将 mpvRx 的 mpv HOOK GLSL（Glow / Frame Extend / YouTube）移植为 AGSL RuntimeShader。
// 关键差异：mpvRx 直接改写视频输出（会拉伸），这里只在捕获到新帧时把处理结果渲染到一张位图，
// 由 Compose 在信箱黑边后方绘制，主视频保持独立，不产生拉伸。仅 API 33+ 可用，低版本回退模糊。

internal data class AmbientParams(
    val blurSamples: Int,
    val maxRadius: Float,
    val glowIntensity: Float,
    val satBoost: Float,
    val warmth: Float,
    val fadeCurve: Float,
    val vignetteStrength: Float,
    val opacity: Float,
    val sampleBudget: Int,
    val extendStrength: Float,
    val detailProtection: Float,
    val glowMix: Float,
    val ditherNoise: Float,
    val bezelDepth: Float,
) {
    companion object {
        fun of(
            mode: AmbientMode,
            quality: AmbientQuality,
        ): AmbientParams = when (mode) {
            AmbientMode.GLOW -> glow(quality)
            AmbientMode.FRAME_EXTEND -> frameExtend(quality)
            AmbientMode.YOUTUBE -> youtube()
        }

        private fun glow(quality: AmbientQuality): AmbientParams = when (quality) {
            AmbientQuality.FAST -> base().copy(
                blurSamples = 8,
                maxRadius = 0.15f,
                glowIntensity = 1.2f,
                satBoost = 1.0f,
                vignetteStrength = 0.3f,
                fadeCurve = 1.2f,
                opacity = 0.8f,
            )

            AmbientQuality.BALANCED -> base().copy(
                blurSamples = 18,
                maxRadius = 0.28f,
                glowIntensity = 1.45f,
                satBoost = 1.25f,
                vignetteStrength = 0.55f,
                fadeCurve = 1.7f,
                opacity = 1.0f,
            )

            AmbientQuality.HIGH_QUALITY -> base().copy(
                blurSamples = 24,
                maxRadius = 0.35f,
                glowIntensity = 1.5f,
                satBoost = 1.3f,
                vignetteStrength = 0.7f,
                fadeCurve = 1.8f,
                opacity = 1.0f,
            )
        }

        private fun frameExtend(quality: AmbientQuality): AmbientParams = when (quality) {
            AmbientQuality.FAST -> base().copy(
                sampleBudget = 8,
                extendStrength = 0.40f,
                detailProtection = 0.86f,
                glowMix = 0.30f,
                ditherNoise = 0.0f,
                vignetteStrength = 0.3f,
                opacity = 0.8f,
            )

            AmbientQuality.BALANCED -> base().copy(
                sampleBudget = 24,
                extendStrength = 0.70f,
                detailProtection = 0.72f,
                glowMix = 0.12f,
                ditherNoise = 0.020f,
                vignetteStrength = 0.55f,
                opacity = 1.0f,
            )

            AmbientQuality.HIGH_QUALITY -> base().copy(
                sampleBudget = 32,
                extendStrength = 0.84f,
                detailProtection = 0.62f,
                glowMix = 0.08f,
                ditherNoise = 0.028f,
                vignetteStrength = 0.62f,
                opacity = 1.0f,
            )
        }

        private fun youtube(): AmbientParams = base().copy(opacity = 1.0f)

        private fun base(): AmbientParams = AmbientParams(
            blurSamples = 18,
            maxRadius = 0.28f,
            glowIntensity = 1.45f,
            satBoost = 1.25f,
            warmth = 0.0f,
            fadeCurve = 1.7f,
            vignetteStrength = 0.55f,
            opacity = 1.0f,
            sampleBudget = 24,
            extendStrength = 0.70f,
            detailProtection = 0.72f,
            glowMix = 0.12f,
            ditherNoise = 0.020f,
            bezelDepth = 0.0f,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal object AmbientShaderRenderer {

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    // 着色器源码按 (mode, 采样数) 缓存，参数走 uniform，无需频繁重建
    private val shaderCache = HashMap<String, RuntimeShader>()

    // 将捕获帧渲染为覆盖全屏的氛围位图。frame 为屏幕上视频显示区域的截图（即视频内容）。
    fun render(
        frame: Bitmap,
        mode: AmbientMode,
        params: AmbientParams,
        outputWidth: Int,
        outputHeight: Int,
    ): Bitmap? {
        if (outputWidth <= 0 || outputHeight <= 0) return null
        if (frame.width <= 0 || frame.height <= 0) return null

        val outputAspect = outputWidth.toFloat() / outputHeight.toFloat()
        val frameAspect = frame.width.toFloat() / frame.height.toFloat()
        // 视频以 fit 方式居中显示，边距即氛围区域。scaleX/scaleY >= 1
        val fittedWidth: Float
        val fittedHeight: Float
        if (frameAspect >= outputAspect) {
            fittedWidth = outputWidth.toFloat()
            fittedHeight = outputWidth / frameAspect
        } else {
            fittedHeight = outputHeight.toFloat()
            fittedWidth = outputHeight * frameAspect
        }
        val scaleX = (outputWidth / fittedWidth).coerceAtLeast(1f)
        val scaleY = (outputHeight / fittedHeight).coerceAtLeast(1f)

        val shader = runCatching { obtainShader(mode, params) }.getOrNull() ?: return null

        val frameShader = BitmapShader(frame, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setInputShader("content", frameShader)
        shader.setFloatUniform("iResolution", outputWidth.toFloat(), outputHeight.toFloat())
        shader.setFloatUniform("uFrameSize", frame.width.toFloat(), frame.height.toFloat())
        shader.setFloatUniform("uScale", scaleX, scaleY)
        applyParamUniforms(shader, mode, params)

        return runCatching {
            val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint().apply { this.shader = shader }
            canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), paint)
            output
        }.getOrNull()
    }

    private fun applyParamUniforms(
        shader: RuntimeShader,
        mode: AmbientMode,
        params: AmbientParams,
    ) {
        shader.setFloatUniform("uVignette", params.vignetteStrength)
        shader.setFloatUniform("uOpacity", params.opacity)
        shader.setFloatUniform("uBezel", params.bezelDepth)
        when (mode) {
            AmbientMode.GLOW -> {
                shader.setFloatUniform("uMaxRadius", params.maxRadius)
                shader.setFloatUniform("uGlowIntensity", params.glowIntensity)
                shader.setFloatUniform("uSatBoost", params.satBoost)
                shader.setFloatUniform("uWarmth", params.warmth)
                shader.setFloatUniform("uFadeCurve", params.fadeCurve)
            }

            AmbientMode.FRAME_EXTEND -> {
                shader.setFloatUniform("uExtendStrength", params.extendStrength)
                shader.setFloatUniform("uDetailProtect", params.detailProtection)
                shader.setFloatUniform("uGlowMix", params.glowMix)
                shader.setFloatUniform("uDitherNoise", params.ditherNoise)
            }

            AmbientMode.YOUTUBE -> Unit
        }
    }

    private fun obtainShader(
        mode: AmbientMode,
        params: AmbientParams,
    ): RuntimeShader {
        val source = when (mode) {
            AmbientMode.GLOW -> glowSource(params.blurSamples.coerceIn(4, 32))
            AmbientMode.FRAME_EXTEND -> frameExtendSource(params.sampleBudget.coerceIn(8, 32))
            AmbientMode.YOUTUBE -> youtubeSource()
        }
        return shaderCache.getOrPut(source) { RuntimeShader(source) }
    }

    // ---- AGSL 源码：忠实移植 mpvRx AmbientShaderBuilder 的算法 ----

    private const val COMMON_HEADER = """
        uniform shader content;
        uniform float2 iResolution;
        uniform float2 uFrameSize;
        uniform float2 uScale;
        uniform float uVignette;
        uniform float uOpacity;
        uniform float uBezel;

        half3 sampleFrame(float2 uv) {
            float2 c = clamp(uv, float2(0.0), float2(1.0)) * uFrameSize;
            return content.eval(c).rgb;
        }

        float rand(float2 seed) {
            return fract(sin(dot(seed, float2(12.9898, 78.233))) * 43758.5453);
        }

        float luma(half3 rgb) {
            return float(dot(rgb, half3(0.2126, 0.7152, 0.0722)));
        }
    """

    private fun glowSource(samples: Int): String {
        val taps = buildSpiralTaps(samples) { radiusNorm, _ -> radiusNorm }
        return """
            $COMMON_HEADER
            uniform float uMaxRadius;
            uniform float uGlowIntensity;
            uniform float uSatBoost;
            uniform float uWarmth;
            uniform float uFadeCurve;

            const int BLUR_SAMPLES = $samples;
            const float3 GLOW_TAPS[$samples] = float3[$samples](
$taps
            );

            half3 adjustSaturation(half3 rgb, float amount) {
                return mix(half3(luma(rgb)), rgb, half(amount));
            }

            half3 applyWarmth(half3 rgb, float amount) {
                rgb.r = clamp(rgb.r + half(amount * 0.060), half(0.0), half(1.0));
                rgb.g = clamp(rgb.g + half(amount * 0.025), half(0.0), half(1.0));
                rgb.b = clamp(rgb.b - half(amount * 0.080), half(0.0), half(1.0));
                return rgb;
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / iResolution;
                float2 videoUv = (uv - 0.5) * uScale + 0.5;
                if (videoUv.x >= 0.0 && videoUv.x <= 1.0 && videoUv.y >= 0.0 && videoUv.y <= 1.0) {
                    return half4(sampleFrame(videoUv), 1.0);
                }

                float2 edgeOrigin = clamp(videoUv, float2(0.0), float2(1.0));
                float edgeDist = length(videoUv - edgeOrigin);
                float edgeFade = exp(-edgeDist * (3.0 / max(uMaxRadius, 0.001)));

                float jitter = rand(uv * iResolution) * 6.2831853;
                float js = sin(jitter);
                float jc = cos(jitter);
                float2 aspectFix = float2(iResolution.y / iResolution.x, 1.0);

                half3 accColor = half3(0.0);
                float accWeight = 0.0;
                for (int i = 0; i < BLUR_SAMPLES; i++) {
                    float3 tap = GLOW_TAPS[i];
                    float2 baseOffset = tap.xy * uMaxRadius;
                    float r = tap.z * uMaxRadius;
                    float2 offset = float2(
                        baseOffset.x * jc - baseOffset.y * js,
                        baseOffset.x * js + baseOffset.y * jc
                    ) * aspectFix;
                    float2 sampleUv = clamp(edgeOrigin + offset, float2(0.0), float2(1.0));
                    half3 sampleRgb = sampleFrame(sampleUv);
                    float distW = pow(max(1.0 / (1.0 + r * 40.0), 0.0), uFadeCurve);
                    float lumaW = 1.0 + luma(sampleRgb) * 2.0;
                    float weight = distW * lumaW;
                    accColor += sampleRgb * half(weight);
                    accWeight += weight;
                }

                half3 glow = (accColor / half(max(accWeight, 1e-5))) * half(uGlowIntensity);
                glow = adjustSaturation(glow, uSatBoost);
                glow = applyWarmth(glow, uWarmth);
                glow *= half(edgeFade);

                float vigR = length(uv - 0.5) * 2.0;
                glow *= half(mix(1.0, smoothstep(1.3, 0.1, vigR), uVignette));

                return half4(glow * half(uOpacity), half(1.0));
            }
        """.trimIndent()
    }

    private fun youtubeSource(): String = """
        $COMMON_HEADER

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / iResolution;
            float2 videoUv = (uv - 0.5) * uScale + 0.5;
            if (videoUv.x >= 0.0 && videoUv.x <= 1.0 && videoUv.y >= 0.0 && videoUv.y <= 1.0) {
                return half4(sampleFrame(videoUv), half(1.0));
            }

            half3 avgColor = half3(0.0);
            const int SAMPLES = 20;
            float baseSeed = 42.0;
            for (int i = 0; i < SAMPLES; i++) {
                float seed = baseSeed + float(i) * 0.618034;
                float x = rand(float2(seed, 0.123));
                float y = rand(float2(seed, 0.456));
                avgColor += sampleFrame(float2(x, y));
            }
            avgColor /= half(float(SAMPLES));

            float l = luma(avgColor);
            avgColor = mix(half3(l), avgColor, half(1.3));
            avgColor *= half(0.30);

            float2 edgeUv = clamp(videoUv, float2(0.0), float2(1.0));
            float dist = length(videoUv - edgeUv);
            float fade = exp(-dist * 2.5);
            avgColor *= half(fade);

            float dither = rand(uv * 1000.0) * 0.004 - 0.002;
            avgColor = clamp(avgColor + half(dither), half(0.0), half(1.0));

            float vigR = length(uv - 0.5) * 2.0;
            return half4(avgColor * half(uOpacity * fade * mix(1.0, smoothstep(1.3, 0.1, vigR), uVignette)), half(1.0));
        }
    """.trimIndent()

    private fun frameExtendSource(sampleBudget: Int): String {
        val budget = sampleBudget.coerceIn(8, 32)
        val extendSteps = (budget / 5).coerceIn(4, 8)
        val glowSamples = (budget / 3).coerceIn(6, 14)
        val anchorRadius = if (budget >= 28) 2 else 1
        val orthoRadius = 1
        val glowTaps = buildSpiralTaps(glowSamples) { _, indexNorm -> indexNorm }
        return """
            $COMMON_HEADER
            uniform float uExtendStrength;
            uniform float uDetailProtect;
            uniform float uGlowMix;
            uniform float uDitherNoise;

            const int EXTEND_STEPS = $extendSteps;
            const int GLOW_SAMPLES = $glowSamples;
            const int ANCHOR_RADIUS = $anchorRadius;
            const int ORTHO_RADIUS = $orthoRadius;
            const float3 FRAME_GLOW_TAPS[$glowSamples] = float3[$glowSamples](
$glowTaps
            );

            float noiseValue(float2 uv) {
                return rand(uv * iResolution + float2(11.0, 47.0)) - 0.5;
            }

            half3 applyDither(half3 rgb, float2 uv, float flatness) {
                if (uDitherNoise <= 0.0001) { return rgb; }
                float amount = uDitherNoise * mix(0.025, 0.15, flatness);
                return clamp(rgb + half3(half(noiseValue(uv) * amount)), 0.0, 1.0);
            }

            float edgeRisk(float2 edgeOrigin, half3 edge, float2 inwardDir, float2 orthoDir) {
                half3 orthoA = sampleFrame(clamp(edgeOrigin + orthoDir * 0.008, float2(0.0), float2(1.0)));
                half3 orthoB = sampleFrame(clamp(edgeOrigin - orthoDir * 0.008, float2(0.0), float2(1.0)));
                half3 inward = sampleFrame(clamp(edgeOrigin + inwardDir * 0.014, float2(0.0), float2(1.0)));
                float orthoContrast = clamp(length(orthoA - orthoB) * 1.9, 0.0, 1.0);
                float inwardContrast = clamp(length(inward - edge) * 2.1, 0.0, 1.0);
                return clamp(orthoContrast * 0.65 + inwardContrast * 0.55, 0.0, 1.0);
            }

            half3 sampleSoftGlow(float2 edgeOrigin, float2 uv, float outsideNorm) {
                float jitter = rand(uv * iResolution) * 6.2831853;
                float js = sin(jitter);
                float jc = cos(jitter);
                float radius = mix(0.016, 0.095, outsideNorm);
                float2 aspectFix = float2(iResolution.y / iResolution.x, 1.0);
                half3 acc = half3(0.0);
                float accWeight = 0.0;
                for (int i = 0; i < GLOW_SAMPLES; i++) {
                    float3 tap = FRAME_GLOW_TAPS[i];
                    float fi = tap.z;
                    float2 baseOffset = tap.xy * radius;
                    float2 offset = float2(
                        baseOffset.x * jc - baseOffset.y * js,
                        baseOffset.x * js + baseOffset.y * jc
                    ) * aspectFix;
                    float2 sampleUv = clamp(edgeOrigin + offset, float2(0.0), float2(1.0));
                    half3 sampleRgb = sampleFrame(sampleUv);
                    float weight = (1.15 - fi) * (0.8 + luma(sampleRgb));
                    acc += sampleRgb * half(weight);
                    accWeight += weight;
                }
                return acc / half(max(accWeight, 1e-5));
            }

            half4 traceAnchorStrip(float2 anchorUv, half3 anchorEdge, float2 inwardDir, float2 orthoDir, float outsideNorm) {
                float extendDepth = mix(0.018, 0.34, outsideNorm) * mix(0.80, 1.45, uExtendStrength);
                float orthoScale = mix(0.026, 0.005, uDetailProtect) * (0.45 + outsideNorm * 0.85);
                half3 acc = half3(0.0);
                float accWeight = 0.0;
                half3 prev = anchorEdge;
                float coherenceAcc = 0.0;
                for (int i = 0; i < EXTEND_STEPS; i++) {
                    float fi = float(i + 1) / float(EXTEND_STEPS);
                    float2 baseUv = clamp(anchorUv + inwardDir * (extendDepth * fi), float2(0.0), float2(1.0));
                    half3 stripAcc = half3(0.0);
                    float stripWeight = 0.0;
                    for (int j = -ORTHO_RADIUS; j <= ORTHO_RADIUS; j++) {
                        float tapPos = float(j) / max(float(ORTHO_RADIUS), 1.0);
                        float2 sampleUv = clamp(
                            baseUv + orthoDir * orthoScale * tapPos * (0.55 + fi * 0.90),
                            float2(0.0), float2(1.0));
                        half3 sampleRgb = sampleFrame(sampleUv);
                        float nearPrev = 1.0 - clamp(length(sampleRgb - prev) * 2.0, 0.0, 1.0);
                        float nearEdge = 1.0 - clamp(length(sampleRgb - anchorEdge) * 1.6, 0.0, 1.0);
                        float tapWeight = exp(-abs(tapPos) * mix(1.2, 3.6, uDetailProtect));
                        tapWeight *= mix(0.55, 1.0, nearPrev);
                        tapWeight *= mix(0.45, 1.0, nearEdge);
                        stripAcc += sampleRgb * half(tapWeight);
                        stripWeight += tapWeight;
                    }
                    half3 sampleRgb = stripAcc / half(max(stripWeight, 1e-5));
                    float edgeSimilarity = 1.0 - clamp(length(sampleRgb - anchorEdge) * 1.8, 0.0, 1.0);
                    float stepSimilarity = 1.0 - clamp(length(sampleRgb - prev) * 2.1, 0.0, 1.0);
                    float weight = mix(1.35, 0.25, fi) * mix(0.60, 1.0, edgeSimilarity);
                    acc += sampleRgb * half(weight);
                    accWeight += weight;
                    coherenceAcc += stepSimilarity * edgeSimilarity;
                    prev = sampleRgb;
                }
                half3 extendRgb = acc / half(max(accWeight, 1e-5));
                float coherence = clamp(coherenceAcc / float(EXTEND_STEPS), 0.0, 1.0);
                coherence = pow(coherence, mix(0.85, 2.8, uDetailProtect));
                return half4(extendRgb, half(coherence));
            }

            half4 samplePredictiveFill(float2 edgeOrigin, half3 edgeRgb, float2 inwardDir, float2 orthoDir, float outsideNorm) {
                float anchorSpan = mix(0.010, 0.070, outsideNorm) * mix(0.55, 1.20, uExtendStrength);
                half3 acc = half3(0.0);
                float accWeight = 0.0;
                float confidenceAcc = 0.0;
                for (int k = -ANCHOR_RADIUS; k <= ANCHOR_RADIUS; k++) {
                    float anchorNorm = ANCHOR_RADIUS > 0 ? float(k) / float(ANCHOR_RADIUS) : 0.0;
                    float2 anchorUv = clamp(edgeOrigin + orthoDir * anchorSpan * anchorNorm, float2(0.0), float2(1.0));
                    half3 anchorEdge = sampleFrame(anchorUv);
                    half4 traced = traceAnchorStrip(anchorUv, anchorEdge, inwardDir, orthoDir, outsideNorm);
                    float centerWeight = exp(-abs(anchorNorm) * mix(1.0, 3.2, uDetailProtect));
                    float anchorSimilarity = 1.0 - clamp(length(anchorEdge - edgeRgb) * 2.3, 0.0, 1.0);
                    float confidence = mix(float(traced.a), float(traced.a) * anchorSimilarity, 0.6);
                    float weight = centerWeight * mix(0.35, 1.0, anchorSimilarity) * mix(0.30, 1.0, confidence);
                    acc += traced.rgb * half(weight);
                    accWeight += weight;
                    confidenceAcc += confidence * weight;
                }
                return half4(acc / half(max(accWeight, 1e-5)), half(confidenceAcc / max(accWeight, 1e-5)));
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / iResolution;
                float2 videoUv = (uv - 0.5) * uScale + 0.5;
                if (videoUv.x >= 0.0 && videoUv.x <= 1.0 && videoUv.y >= 0.0 && videoUv.y <= 1.0) {
                    return half4(sampleFrame(videoUv), half(1.0));
                }

                float2 edgeOrigin = clamp(videoUv, float2(0.0), float2(1.0));
                float2 overflow = videoUv - edgeOrigin;
                bool horizontal = abs(overflow.x) >= abs(overflow.y);
                float2 inwardDir = horizontal
                    ? float2(overflow.x < 0.0 ? 1.0 : -1.0, 0.0)
                    : float2(0.0, overflow.y < 0.0 ? 1.0 : -1.0);
                float2 orthoDir = horizontal ? float2(0.0, 1.0) : float2(1.0, 0.0);

                float barExtent = horizontal
                    ? max((uScale.x - 1.0) * 0.5, 0.001)
                    : max((uScale.y - 1.0) * 0.5, 0.001);
                float distToEdge = horizontal ? abs(overflow.x) : abs(overflow.y);
                float outsideNorm = clamp(distToEdge / barExtent, 0.0, 1.0);

                half3 edgeRgb = sampleFrame(edgeOrigin);
                float risk = edgeRisk(edgeOrigin, edgeRgb, inwardDir, orthoDir);
                half4 extendResult = samplePredictiveFill(edgeOrigin, edgeRgb, inwardDir, orthoDir, outsideNorm);
                half3 glowRgb = sampleSoftGlow(edgeOrigin, uv, outsideNorm);

                float confidence = clamp(float(extendResult.a) * (1.0 - risk * mix(0.45, 0.88, uDetailProtect)), 0.0, 1.0);
                float fallbackMix = clamp(
                    uGlowMix + (1.0 - confidence) * mix(0.18, 0.75, uDetailProtect) + risk * mix(0.05, 0.28, uDetailProtect),
                    0.0, 1.0);
                half3 fillRgb = mix(extendResult.rgb, glowRgb, half(fallbackMix));
                fillRgb = mix(edgeRgb, fillRgb, half(smoothstep(0.18, 0.98, outsideNorm)));

                float vigR = length(uv - 0.5) * 2.0;
                fillRgb *= half(mix(1.0, smoothstep(1.3, 0.1, vigR), uVignette));

                float flatness = clamp((1.0 - risk) * (0.55 + outsideNorm * 0.45), 0.0, 1.0);
                fillRgb = applyDither(fillRgb, uv, flatness);

                return half4(fillRgb * half(uOpacity), half(1.0));
            }
        """.trimIndent()
    }

    private const val GOLDEN_ANGLE = 2.399963229728653

    private fun buildSpiralTaps(
        samples: Int,
        thirdComponent: (radiusNorm: Double, indexNorm: Double) -> Double,
    ): String {
        val count = samples.coerceAtLeast(1)
        return (0 until count).joinToString(",\n") { index ->
            val indexNorm = (index.toDouble() + 0.5) / count.toDouble()
            val radiusNorm = sqrt(indexNorm)
            val theta = (index.toDouble() + 0.5) * GOLDEN_ANGLE
            val x = cos(theta) * radiusNorm
            val y = sin(theta) * radiusNorm
            "                float3(${glsl(x)}, ${glsl(y)}, ${glsl(thirdComponent(radiusNorm, indexNorm))})"
        }
    }

    private fun glsl(value: Double): String {
        val normalized = if (kotlin.math.abs(value) < 0.0000005) 0.0 else value
        val formatted = String.format(Locale.US, "%.8f", normalized).trimEnd('0').trimEnd('.')
        return if (formatted.contains('.')) formatted else "$formatted.0"
    }
}
