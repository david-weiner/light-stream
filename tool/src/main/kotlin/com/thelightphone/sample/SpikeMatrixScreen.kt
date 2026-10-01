package com.thelightphone.sample

// Spike diagnostic only (spike/bandcamp-500-http-stack, Task 1 — see TASK_1_SPIKE_HANDOFF.md).
// Temporarily the app's @InitialScreen for this branch so it can run against real,
// already-stored Bandcamp credentials without touching HomeScreen or any other production
// screen's behavior. Delete this file (and revert @InitialScreen back to HomeScreen) before
// this branch is ever merged.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.sample.subsonic.SpikeCellResult
import com.thelightphone.sample.subsonic.SubsonicCredentialStore
import com.thelightphone.sample.subsonic.SubsonicSpikeHarness
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SpikeUiState {
    data object NoStoredCredentials : SpikeUiState()
    data object Ready : SpikeUiState()
    data object Running : SpikeUiState()
    data class Done(val results: List<SpikeCellResult>) : SpikeUiState()
    data class Error(val message: String) : SpikeUiState()
}

class SpikeMatrixViewModel(
    private val credentialStore: SubsonicCredentialStore,
) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow<SpikeUiState>(SpikeUiState.Ready)
    val state: StateFlow<SpikeUiState> = _state

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val stored = credentialStore.load()
            withContext(Dispatchers.Main) {
                _state.value = if (stored == null) SpikeUiState.NoStoredCredentials else SpikeUiState.Ready
            }
        }
    }

    fun run() {
        _state.value = SpikeUiState.Running
        viewModelScope.launch(Dispatchers.IO) {
            val stored = credentialStore.load()
            if (stored == null) {
                withContext(Dispatchers.Main) { _state.value = SpikeUiState.NoStoredCredentials }
                return@launch
            }
            val results = runCatching {
                SubsonicSpikeHarness().runMatrix(stored.username, stored.password)
            }
            withContext(Dispatchers.Main) {
                _state.value = results.fold(
                    onSuccess = { SpikeUiState.Done(it) },
                    onFailure = { error -> SpikeUiState.Error(error.message ?: "Matrix run failed.") },
                )
            }
        }
    }
}

@InitialScreen
class SpikeMatrixScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SpikeMatrixViewModel>(sealedActivity) {

    override val viewModelClass: Class<SpikeMatrixViewModel>
        get() = SpikeMatrixViewModel::class.java

    override fun createViewModel(): SpikeMatrixViewModel {
        return SpikeMatrixViewModel(SubsonicCredentialStore(lightContext.dataStore))
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
                Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
                    LightText(
                        text = "Spike: HTTP stack matrix",
                        variant = LightTextVariant.Heading,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    when (val mode = state) {
                        is SpikeUiState.NoStoredCredentials -> {
                            LightText(
                                text = "No stored Bandcamp credentials found. Log in once via the " +
                                    "regular main-branch build first, then relaunch this spike build.",
                                variant = LightTextVariant.Copy,
                                lighten = true,
                            )
                        }
                        is SpikeUiState.Ready -> {
                            LightText(
                                text = "Found stored credentials. Tap RUN to fire getArtists through " +
                                    "cells A–D and record the results.",
                                variant = LightTextVariant.Copy,
                                lighten = true,
                            )
                        }
                        is SpikeUiState.Running -> {
                            LightText(text = "Running…", variant = LightTextVariant.Copy, lighten = true)
                        }
                        is SpikeUiState.Error -> {
                            LightText(text = "Harness error: ${mode.message}", variant = LightTextVariant.Copy)
                        }
                        is SpikeUiState.Done -> {
                            LightScrollView(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                mode.results.forEach { cell -> CellResultBlock(cell) }
                            }
                        }
                    }
                }

                LightBottomBar(
                    items = listOf(
                        LightBarButton.Text(
                            text = if (state is SpikeUiState.Running) "RUNNING…" else "RUN",
                            onClick = { if (state !is SpikeUiState.Running) viewModel.run() },
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun CellResultBlock(cell: SpikeCellResult) {
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column {
            LightText(text = cell.label, variant = LightTextVariant.Copy)
            LightText(
                text = "status: ${cell.statusCode ?: "n/a"}${cell.error?.let { " error: $it" } ?: ""}",
                variant = LightTextVariant.Detail,
                lighten = true,
            )
            LightText(text = "request: ${cell.requestLine}", variant = LightTextVariant.Detail, lighten = true)
            LightText(text = "headers: ${cell.headers}", variant = LightTextVariant.Detail, lighten = true)
            LightText(text = "body: ${cell.bodySnippet}", variant = LightTextVariant.Detail, lighten = true)
        }
    }
}
