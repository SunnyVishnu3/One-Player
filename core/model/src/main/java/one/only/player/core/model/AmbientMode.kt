package one.only.player.core.model

// 氛围背景可视模式，移植自 mpvRx（Glow / Frame Extend / YouTube）
// 在 only_player 中以 Compose + AGSL RuntimeShader 重实现，不改写主视频输出，避免拉伸
enum class AmbientMode {
    GLOW,
    FRAME_EXTEND,
    YOUTUBE,
}
