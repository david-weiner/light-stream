package com.thelightphone.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.thelightphone.sample.subsonic.QrCredentialsParser
import com.thelightphone.sample.subsonic.SubsonicCredentials
import com.thelightphone.sdk.LightQrCodeScanner
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController

/**
 * Scans the code from web/pair.html. Only parses the payload into credentials;
 * HomeScreen owns actually attempting the Subsonic login with whatever comes back.
 */
class QrLoginScreen(sealedActivity: SealedLightActivity) :
    SimpleLightScreen<Result<SubsonicCredentials>>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        var pendingScan by remember { mutableStateOf<String?>(null) }

        LightTheme(colors = themeColors) {
            LightQrCodeScanner(
                title = "Scan pairing code",
                onScanned = { pendingScan = it },
                onBack = { goBack() },
            )
        }

        LaunchedEffect(pendingScan) {
            val value = pendingScan ?: return@LaunchedEffect
            goBack(QrCredentialsParser.parse(value))
        }
    }
}
