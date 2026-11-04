// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.myxomatom.lutrin.R
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.navigateToReader
import io.github.myxomatom.lutrin.feature.reader.readerScreen

private const val HOME_ROUTE = "home"

/** MIME type offered by the system file picker (F01). */
private const val PDF_MIME_TYPE = "application/pdf"

/**
 * Root composable: the navigation graph of the app. The home destination is a placeholder
 * until the library screen (E1, milestone M3).
 *
 * @param modifier modifier applied to the navigation host.
 */
@Composable
fun LutrinApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = HOME_ROUTE, modifier = modifier) {
        composable(HOME_ROUTE) {
            val picker =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                    uri?.let(navController::navigateToReader)
                }
            WelcomeScreen(onOpenDocument = { picker.launch(arrayOf(PDF_MIME_TYPE)) })
        }
        readerScreen(onBack = navController::popBackStack)
    }
}

/**
 * Temporary home screen: what Lutrin is, and a button opening the system file picker.
 *
 * @param onOpenDocument called when the user wants to pick a PDF.
 */
@Composable
fun WelcomeScreen(onOpenDocument: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onOpenDocument) { Text(stringResource(R.string.welcome_open_document)) }
            Text(
                text = stringResource(R.string.welcome_status),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview
@Composable
private fun WelcomeScreenPreview() {
    LutrinTheme(dynamicColor = false) { WelcomeScreen(onOpenDocument = {}) }
}
