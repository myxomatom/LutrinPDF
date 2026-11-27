// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.settings.AppLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LocaleManagerAppLanguageControllerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun controller(sdkInt: Int = Build.VERSION.SDK_INT) =
        LocaleManagerAppLanguageController(context, UnconfinedTestDispatcher(), sdkInt)

    @Test
    fun `the app chooses its language from Android 13`() {
        assertThat(controller().isSupported).isTrue()
        assertThat(controller(sdkInt = Build.VERSION_CODES.TIRAMISU).isSupported).isTrue()
        assertThat(controller(sdkInt = Build.VERSION_CODES.O).isSupported).isFalse()
    }

    @Test
    fun `the language of the system is the default`() = runTest {
        assertThat(controller().current()).isEqualTo(AppLanguage.SYSTEM)
    }

    @Test
    fun `a chosen language is handed to the system and read back`() = runTest {
        val controller = controller()

        controller.set(AppLanguage.FRENCH)
        assertThat(controller.current()).isEqualTo(AppLanguage.FRENCH)
        assertThat(context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
            .isEqualTo("fr")

        controller.set(AppLanguage.ENGLISH)
        assertThat(controller.current()).isEqualTo(AppLanguage.ENGLISH)
    }

    @Test
    fun `choosing the language of the system gives the choice back to the system`() = runTest {
        val controller = controller()
        controller.set(AppLanguage.FRENCH)

        controller.set(AppLanguage.SYSTEM)

        assertThat(controller.current()).isEqualTo(AppLanguage.SYSTEM)
        assertThat(context.getSystemService(LocaleManager::class.java).applicationLocales.isEmpty).isTrue()
    }

    @Test
    fun `before Android 13 the app offers no choice and changes nothing`() = runTest {
        val controller = controller(sdkInt = Build.VERSION_CODES.S_V2)

        assertThat(controller.isSupported).isFalse()
        controller.set(AppLanguage.FRENCH)
        assertThat(controller.current()).isEqualTo(AppLanguage.SYSTEM)
    }
}
