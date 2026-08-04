package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import com.thelightphone.sample.library.Track
import com.thelightphone.sample.subsonic.SubsonicClient
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class TracksUiState {
    data object Loading : TracksUiState()
    data class Loaded(val tracks: List<Track>) : TracksUiState()
    data class Error(val message: String) : TracksUiState()
}

class TracksViewModel(
    private val subsonicClient: SubsonicClient,
    private val albumId: String,
) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow<TracksUiState>(TracksUiState.Loading)
    val state: StateFlow<TracksUiState> = _state

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val result = subsonicClient.getTracks(albumId)
            withContext(Dispatchers.Main) {
                _state.value = result.fold(
                    onSuccess = { tracks ->
                        TracksUiState.Loaded(tracks.sortedBy { it.trackNumber ?: Int.MAX_VALUE })
                    },
                    onFailure = { error -> TracksUiState.Error(error.message ?: "Couldn't load tracks.") },
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        subsonicClient.close()
    }
}

class TracksScreen(
    sealedActivity: SealedLightActivity,
    private val albumId: String,
    private val albumTitle: String,
    private val artistName: String,
) : LightScreen<Unit, TracksViewModel>(sealedActivity) {

    override val viewModelClass: Class<TracksViewModel>
        get() = TracksViewModel::class.java

    override fun createViewModel(): TracksViewModel {
        return TracksViewModel(SubsonicClient(lightContext.dataStore), albumId)
    }

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                    ),
                    center = LightTopBarCenter.TwoLineDetail(albumTitle, artistName),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                when (val mode = state) {
                    is TracksUiState.Loading -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LightText(text = "Loading…", variant = LightTextVariant.Copy, lighten = true)
                        }
                    }

                    is TracksUiState.Error -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LightText(
                                text = mode.message,
                                variant = LightTextVariant.Copy,
                                align = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                            )
                        }
                    }

                    is TracksUiState.Loaded -> {
                        if (mode.tracks.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                LightText(text = "No tracks found.", variant = LightTextVariant.Copy, lighten = true)
                            }
                        } else {
                            LightScrollView(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(start = 1f.gridUnitsAsDp()),
                            ) {
                                mode.tracks.forEach { track ->
                                    TrackRow(
                                        track = track,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 0.75f.gridUnitsAsDp()),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            text = track.trackNumber?.toString()?.padStart(2, '0') ?: "—",
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(end = 1f.gridUnitsAsDp()),
        )
        LightText(
            text = track.title,
            variant = LightTextVariant.Copy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        LightText(
            text = formatDuration(track.durationSeconds),
            variant = LightTextVariant.Detail,
            lighten = true,
        )
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
