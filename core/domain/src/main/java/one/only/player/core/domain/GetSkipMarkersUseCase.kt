package one.only.player.core.domain

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import one.only.player.core.common.Dispatcher
import one.only.player.core.common.Logger
import one.only.player.core.common.NextDispatchers
import one.only.player.core.data.repository.IntroDbLookupOutcome
import one.only.player.core.data.repository.IntroDbLookupRequest
import one.only.player.core.data.repository.IntroDbRepository
import one.only.player.core.data.repository.IntroDbSegment
import one.only.player.core.model.IntroSegmentProvider

class GetSkipMarkersUseCase @Inject constructor(
    private val introDbRepository: IntroDbRepository,
    @Dispatcher(NextDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(
        mediaTitle: String,
        season: Int? = null,
        episode: Int? = null,
        provider: IntroSegmentProvider = IntroSegmentProvider.INTRO_DB,
    ): Flow<List<IntroDbSegment>> = flow {
        val request = IntroDbLookupRequest(
            mediaTitle = mediaTitle,
            season = season,
            episode = episode,
            provider = provider,
        )
        try {
            val outcome = introDbRepository.lookupSegments(request)
            if (outcome is IntroDbLookupOutcome.Loaded) {
                emit(outcome.segments)
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            Logger.error("GetSkipMarkersUseCase", "Failed to fetch skip markers: ${e.message}")
            emit(emptyList())
        }
    }.flowOn(ioDispatcher)
}
