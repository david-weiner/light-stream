package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sample.subsonic.SubsonicClient
import com.thelightphone.sample.subsonic.SubsonicClientLoginResult
import com.thelightphone.sample.subsonic.SubsonicCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EditingField { USERNAME, PASSWORD }

sealed class LoginUiState {
    data object CheckingStoredLogin : LoginUiState()

    data class ChooseEntryMethod(val errorMessage: String? = null) : LoginUiState()

    data class LoginForm(
        val editing: EditingField? = null,
        val username: String = "",
        val password: String = "",
        val errorMessage: String? = null,
        val isSubmitting: Boolean = false,
    ) : LoginUiState()

    data class LoggedIn(val username: String) : LoginUiState()
}

class HomeScreenViewModel(
    private val subsonicClient: SubsonicClient,
) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.CheckingStoredLogin)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val result = subsonicClient.checkStoredLogin()
            withContext(Dispatchers.Main) {
                _state.value = when (result) {
                    is SubsonicClientLoginResult.Success -> LoginUiState.LoggedIn(result.username)
                    SubsonicClientLoginResult.NoStoredLogin -> LoginUiState.ChooseEntryMethod()
                }
            }
        }
    }

    fun chooseManualEntry() {
        _state.value = LoginUiState.LoginForm()
    }

    fun backToEntryChoice() {
        _state.value = LoginUiState.ChooseEntryMethod()
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            subsonicClient.logout()
            withContext(Dispatchers.Main) {
                _state.value = LoginUiState.ChooseEntryMethod()
            }
        }
    }

    fun onQrScanResult(result: Result<SubsonicCredentials>) {
        result.fold(
            onSuccess = { credentials -> attemptLogin(credentials.username, credentials.password) },
            onFailure = { error ->
                _state.value = LoginUiState.ChooseEntryMethod(
                    errorMessage = error.message ?: "Couldn't read that QR code.",
                )
            },
        )
    }

    fun beginEditingUsername() = updateForm { it.copy(editing = EditingField.USERNAME, errorMessage = null) }

    fun beginEditingPassword() = updateForm { it.copy(editing = EditingField.PASSWORD, errorMessage = null) }

    fun cancelEditing() = updateForm { it.copy(editing = null) }

    fun commitField(field: EditingField, text: String) = updateForm { form ->
        when (field) {
            EditingField.USERNAME -> form.copy(username = text, editing = null)
            EditingField.PASSWORD -> form.copy(password = text, editing = null)
        }
    }

    fun submitLogin() {
        val form = _state.value as? LoginUiState.LoginForm ?: return
        val username = form.username.trim()
        val password = form.password
        if (username.isEmpty() || password.isEmpty()) {
            updateForm { it.copy(errorMessage = "Enter a username and password.") }
            return
        }
        attemptLogin(username, password)
    }

    private fun attemptLogin(username: String, password: String) {
        _state.value = LoginUiState.LoginForm(username = username, password = password, isSubmitting = true)
        viewModelScope.launch(Dispatchers.IO) {
            val result = subsonicClient.login(username, password)
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { _state.value = LoginUiState.LoggedIn(username) },
                    onFailure = { error ->
                        _state.value = LoginUiState.LoginForm(
                            username = username,
                            password = password,
                            errorMessage = error.message ?: "Login failed.",
                        )
                    },
                )
            }
        }
    }

    private fun updateForm(transform: (LoginUiState.LoginForm) -> LoginUiState.LoginForm) {
        _state.update { current -> (current as? LoginUiState.LoginForm)?.let(transform) ?: current }
    }

    override fun onCleared() {
        super.onCleared()
        subsonicClient.close()
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, HomeScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeScreenViewModel>
        get() = HomeScreenViewModel::class.java

    override fun createViewModel(): HomeScreenViewModel {
        return HomeScreenViewModel(SubsonicClient(lightContext.dataStore))
    }

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()
        val keyboardOptionsFlow = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            when (val mode = state) {
                is LoginUiState.CheckingStoredLogin -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LightThemeTokens.colors.background)
                            .padding(32.dp),
                    ) {
                        LightText(
                            text = "Light Stream",
                            variant = LightTextVariant.Heading,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                        LightText(
                            text = "Checking stored login…",
                            variant = LightTextVariant.Copy,
                            lighten = true,
                        )
                    }
                }

                is LoginUiState.ChooseEntryMethod -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LightThemeTokens.colors.background),
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(32.dp),
                        ) {
                            LightText(
                                text = "Light Stream",
                                variant = LightTextVariant.Heading,
                                modifier = Modifier.padding(bottom = 16.dp),
                            )
                            LightText(
                                text = "Log in with your Bandcamp Fan Settings streaming username and password.",
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                modifier = Modifier.padding(bottom = 24.dp),
                            )
                            LightText(
                                text = "Scan a pairing code from another device, or type the two strings in by hand.",
                                variant = LightTextVariant.Detail,
                                lighten = true,
                            )
                            mode.errorMessage?.let {
                                LightText(
                                    text = it,
                                    variant = LightTextVariant.Detail,
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                            }
                        }

                        LightBottomBar(
                            items = listOf(
                                LightBarButton.Text(text = "MANUAL ENTRY", onClick = viewModel::chooseManualEntry),
                                LightBarButton.Text(
                                    text = "SCAN QR",
                                    onClick = {
                                        navigateTo(screenFactory = { QrLoginScreen(it) }) { result ->
                                            viewModel.onQrScanResult(result)
                                        }
                                    },
                                ),
                            ),
                        )
                    }
                }

                is LoginUiState.LoginForm -> {
                    val editingField = mode.editing
                    if (editingField != null) {
                        val title = if (editingField == EditingField.USERNAME) "Username" else "Password"
                        val initialText = if (editingField == EditingField.USERNAME) mode.username else mode.password
                        val fieldState = rememberTextFieldState(initialText)
                        LightTextInputEditor(
                            title = title,
                            state = fieldState,
                            onSubmit = { text -> viewModel.commitField(editingField, text.toString()) },
                            onBack = viewModel::cancelEditing,
                            keyboardOptionsFlow = keyboardOptionsFlow,
                            singleLine = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(LightThemeTokens.colors.background),
                        ) {
                            LightTopBar(
                                leftButton = LightBarButton.LightIcon(
                                    icon = LightIcons.BACK,
                                    onClick = viewModel::backToEntryChoice,
                                ),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(32.dp),
                            ) {
                                LightText(
                                    text = "Light Stream",
                                    variant = LightTextVariant.Heading,
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )
                                LightText(
                                    text = "Log in with your Bandcamp Fan Settings streaming username and password.",
                                    variant = LightTextVariant.Detail,
                                    lighten = true,
                                    modifier = Modifier.padding(bottom = 24.dp),
                                )
                                LightTextField(
                                    label = "Username",
                                    value = mode.username,
                                    placeholder = "Bandcamp username",
                                    onClick = viewModel::beginEditingUsername,
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )
                                LightTextField(
                                    label = "Password",
                                    value = "•".repeat(mode.password.length),
                                    placeholder = "Bandcamp password",
                                    onClick = viewModel::beginEditingPassword,
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )
                                val status = when {
                                    mode.errorMessage != null -> mode.errorMessage
                                    mode.isSubmitting -> "Logging in…"
                                    else -> null
                                }
                                status?.let {
                                    LightText(
                                        text = it,
                                        variant = LightTextVariant.Detail,
                                        lighten = true,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }

                            LightBottomBar(
                                items = listOf(
                                    null,
                                    LightBarButton.Text(text = "LOG IN", onClick = viewModel::submitLogin),
                                    null,
                                ),
                            )
                        }
                    }
                }

                is LoginUiState.LoggedIn -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LightThemeTokens.colors.background),
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(32.dp),
                        ) {
                            LightText(
                                text = "Light Stream",
                                variant = LightTextVariant.Heading,
                                modifier = Modifier.padding(bottom = 16.dp),
                            )
                            LightText(
                                text = "Logged in as ${mode.username}",
                                variant = LightTextVariant.Copy,
                            )
                        }

                        LightBottomBar(
                            items = listOf(
                                LightBarButton.Text(
                                    text = "LIBRARY",
                                    onClick = { navigateTo(screenFactory = { ArtistsScreen(it) }) },
                                ),
                                LightBarButton.Text(text = "LOG OUT", onClick = viewModel::logout),
                            ),
                        )
                    }
                }
            }
        }
    }
}
