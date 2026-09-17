package com.localqbank.library

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Ben's single model-routing boundary.
 *
 * Local reasoning remains authoritative. The router only chooses an accelerator; it never owns
 * QBank, SRS, notes, learner state, or medical truth. Gemini is deliberately requested only when
 * the question benefits from complex synthesis, current/external evidence, or an explicit cloud
 * action. This keeps simple study interactions fast, private and offline-capable.
 */
class BenModelRouter(context: Context) {
    private val app = context.applicationContext

    enum class Mode { LOCAL, HYBRID }

    data class Route(
        val mode: Mode,
        val label: String,
        val reason: String,
        val cloudEligible: Boolean
    )

    fun route(query: String, authenticated: Boolean): Route {
        val q = query.trim()
        val cloud = hasNetwork()
        val explicitCloud = Regex("(?i)\\b(gemini|web|search|latest|current|recent|guideline|guidelines|evidence|research|pyq|previous year)\\b").containsMatchIn(q)
        val complex = q.length > 220 ||
            Regex("(?i)\\b(compare|differentiate|explain why|mechanism|management|differential|controversy|image|visual|multiple|integrate|synthesize)\\b").containsMatchIn(q)
        return when {
            explicitCloud && cloud -> Route(
                Mode.HYBRID,
                "HYBRID • Ben local + Gemini cloud",
                "External/current evidence or explicit Gemini research requested",
                true
            )
            complex && cloud && authenticated -> Route(
                Mode.HYBRID,
                "HYBRID • Ben local + Gemini cloud",
                "Complex synthesis benefits from cloud reasoning",
                true
            )
            !cloud -> Route(
                Mode.LOCAL,
                "LOCAL • offline Ben",
                "No network: deterministic/local intelligence stays available",
                false
            )
            else -> Route(
                Mode.LOCAL,
                "LOCAL • Ben first",
                "Fast local path selected; Gemini remains optional",
                authenticated
            )
        }
    }

    fun uiSummary(): String {
        val online = hasNetwork()
        return if (online) {
            "🧠 BEN ROUTER  •  LOCAL FIRST  →  GEMINI WHEN USEFUL"
        } else {
            "🧠 BEN ROUTER  •  OFFLINE LOCAL MODE"
        }
    }

    private fun hasNetwork(): Boolean = runCatching {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)
}
