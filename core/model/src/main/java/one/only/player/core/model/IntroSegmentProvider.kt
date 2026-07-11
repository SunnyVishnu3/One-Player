package one.only.player.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class IntroSegmentProvider(val displayName: String, val internalId: String) {
    INTRO_DB("IntroDB", "introdb"),
    THE_INTRO_DB("TheIntroDB", "theintrodb"),
    ANI_SKIP("AniSkip (Anime)", "aniskip"),
    ANIME_SKIP("AnimeSkip (Anime)", "animeskip"),
    TMDB_SKIP("TIDB (TMDB)", "tidb"),
    HYBRID("Hybrid (All)", "hybrid"),
}
