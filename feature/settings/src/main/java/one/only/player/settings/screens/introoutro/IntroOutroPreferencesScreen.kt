package one.only.player.settings.screens.introoutro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.ui.R
import one.only.player.core.ui.components.SettingsContentTopPadding
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.settings.screens.player.PlayerPreferencesViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun IntroOutroPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: PlayerPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(id = R.string.intro_outro_settings),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_intro_outro_settings_back"),
                    ) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        containerColor = MiuixTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(state = rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(top = SettingsContentTopPadding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = stringResource(R.string.pref_online_skip_markers_title),
                    summary = stringResource(R.string.pref_online_skip_markers_summary),
                    checked = uiState.preferences.enableIntroDb,
                    onCheckedChange = { viewModel.updateEnableIntroDb(it) },
                )

                SwitchPreference(
                    title = stringResource(R.string.pref_chapter_detect_title),
                    summary = stringResource(R.string.pref_chapter_detect_summary),
                    checked = uiState.preferences.detectIntroOutroFromChapters,
                    onCheckedChange = { viewModel.updateDetectIntroOutroFromChapters(it) },
                )

                SwitchPreference(
                    title = stringResource(R.string.pref_auto_skip_intro_title),
                    summary = stringResource(R.string.pref_auto_skip_intro_summary),
                    checked = uiState.preferences.autoSkipIntro,
                    onCheckedChange = { viewModel.updateAutoSkipIntro(it) },
                )

                SwitchPreference(
                    title = stringResource(R.string.pref_auto_skip_outro_title),
                    summary = stringResource(R.string.pref_auto_skip_outro_summary),
                    checked = uiState.preferences.autoSkipOutro,
                    onCheckedChange = { viewModel.updateAutoSkipOutro(it) },
                )
            }
        }
    }
}
