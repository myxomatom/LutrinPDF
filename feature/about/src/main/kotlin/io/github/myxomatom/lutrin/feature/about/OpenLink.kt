// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.about

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Hands [url] to the app of the user's choice: `ACTION_VIEW` in `CATEGORY_BROWSABLE`, as the link
 * of a web page would be (ADR 0022, ADR 0030). Lutrin itself never opens it and never reaches the
 * network; no permission is added.
 *
 * @param context where the activity is started from; outside an activity, it starts in a new task.
 * @param url the address of the source code, confirmed by the user.
 * @return `false` when no installed app can open it.
 */
internal fun openSourceCode(context: Context, url: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (expected: ActivityNotFoundException) {
        // Reported to the user by the caller (« Aucune application ne peut ouvrir ce lien »).
        false
    }
}
