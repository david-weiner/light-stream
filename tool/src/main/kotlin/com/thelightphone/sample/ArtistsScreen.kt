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
import com.thelightphone.sample.library.Artist
import com.thelightphone.sample.subsonic.SubsonicClient
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
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
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ArtistsUiState {
    data object Loading : ArtistsUiState()
    data class Loaded(val artists: List<Artist>) : ArtistsUiState()
    data class Error(val message: String) : ArtistsUiState()
}

class ArtistsViewModel(private val subsonicClient: SubsonicClient) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow<ArtistsUiState>(ArtistsUiState.Loading)
    val state: StateFlow<ArtistsUiState> = _state

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val result = subsonicClient.getArtists()
            withContext(Dispatchers.Main) {
                _state.value = result.fold(
                    onSuccess = { ArtistsUiState.Loaded(it) },
                    onFailure = { error -> ArtistsUiState.Error(error.message ?: "Couldn't load artists.") },
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        subsonicClient.close()
    }
}

class ArtistsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, ArtistsViewModel>(sealedActivity) {

    override val viewModelClass: Class<ArtistsViewModel>
        get() = ArtistsViewModel::class.java

    override fun createViewModel(): ArtistsViewModel {
        return ArtistsViewModel(SubsonicClient(lightContext.dataStore))
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
                    center = LightTopBarCenter.Text("Artists"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                when (val mode = state) {
                    is ArtistsUiState.Loading -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LightText(text = "Loading…", variant = LightTextVariant.Copy, lighten = true)
                        }
                    }

                    is ArtistsUiState.Error -> {
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

                    is ArtistsUiState.Loaded -> {
                        if (mode.artists.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                LightText(text = "No artists found.", variant = LightTextVariant.Copy, lighten = true)
                            }
                        } else {
                            LightScrollView(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(start = 1f.gridUnitsAsDp()),
                            ) {
                                mode.artists.forEach { artist ->
                                    ArtistRow(
                                        artist = artist,
                                        modifier = Modifier
                                            .lightClickable {
                                                navigateTo(screenFactory = {
                                                    AlbumsScreen(it, artistId = artist.id, artistName = artist.name)
                                                })
                                            }
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
private fun ArtistRow(artist: Artist, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            text = artist.name,
            variant = LightTextVariant.Copy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        LightIcon(icon = LightIcons.ARROW_RIGHT, size = 1.5f)
    }
}
