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
import com.thelightphone.sample.library.Album
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

sealed class AlbumsUiState {
    data object Loading : AlbumsUiState()
    data class Loaded(val albums: List<Album>) : AlbumsUiState()
    data class Error(val message: String) : AlbumsUiState()
}

class AlbumsViewModel(
    private val subsonicClient: SubsonicClient,
    private val artistId: String,
) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow<AlbumsUiState>(AlbumsUiState.Loading)
    val state: StateFlow<AlbumsUiState> = _state

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val result = subsonicClient.getAlbums(artistId)
            withContext(Dispatchers.Main) {
                _state.value = result.fold(
                    onSuccess = { AlbumsUiState.Loaded(it) },
                    onFailure = { error -> AlbumsUiState.Error(error.message ?: "Couldn't load albums.") },
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        subsonicClient.close()
    }
}

class AlbumsScreen(
    sealedActivity: SealedLightActivity,
    private val artistId: String,
    private val artistName: String,
) : LightScreen<Unit, AlbumsViewModel>(sealedActivity) {

    override val viewModelClass: Class<AlbumsViewModel>
        get() = AlbumsViewModel::class.java

    override fun createViewModel(): AlbumsViewModel {
        return AlbumsViewModel(SubsonicClient(lightContext.dataStore), artistId)
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
                    center = LightTopBarCenter.Text(artistName),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                when (val mode = state) {
                    is AlbumsUiState.Loading -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LightText(text = "Loading…", variant = LightTextVariant.Copy, lighten = true)
                        }
                    }

                    is AlbumsUiState.Error -> {
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

                    is AlbumsUiState.Loaded -> {
                        if (mode.albums.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                LightText(text = "No albums found.", variant = LightTextVariant.Copy, lighten = true)
                            }
                        } else {
                            LightScrollView(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(start = 1f.gridUnitsAsDp()),
                            ) {
                                mode.albums.forEach { album ->
                                    AlbumRow(
                                        album = album,
                                        modifier = Modifier
                                            .lightClickable {
                                                navigateTo(screenFactory = {
                                                    TracksScreen(
                                                        it,
                                                        albumId = album.id,
                                                        albumTitle = album.title,
                                                        artistName = album.artist,
                                                    )
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
private fun AlbumRow(album: Album, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            text = album.title,
            variant = LightTextVariant.Copy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        LightIcon(icon = LightIcons.ARROW_RIGHT, size = 1.5f)
    }
}
