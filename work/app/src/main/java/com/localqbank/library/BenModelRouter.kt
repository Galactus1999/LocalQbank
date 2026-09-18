package com.localqbank.library

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Single accelerator-routing boundary. Local Ben remains authoritative; cloud providers are
 * optional free/cheap accelerators and never own study state, scheduling or medical truth.
 */
class BenModelRouter(context: Context) {
    private val app = context.applicationContext
    private val gateway get() = AppManagers.cloudAi

    enum class Mode { LOCAL, HYBRID }

    data class Route(val mode: Mode, val label: String, val reason: String, val cloudEligible: Boolean)

    fun route(query: String, authenticated: Boolean = false): Route {
        val q = query.trim()
        val online = hasNetwork()
        val freeAi = gateway.configs().any { it.enabled && it.hasKey }
        val explicitCloud = Regex("(?i)\\b(ai|cloud|deep reasoning|synthesize|compare with another view|current|recent|guideline|guidelines|evidence|research)\\b").containsMatchIn(q)
        val complex = q.length > 220 || Regex("(?i)\\b(compare|differentiate|explain why|mechanism|management|differential|controversy|multiple|integrate|synthesize|distractor|options)\\b").containsMatchIn(q)
        return when {
            online && freeAi && (explicitCloud || complex) -> Route(Mode.HYBRID, "HYBRID • Ben local + FREE AI", "Local retrieval first; a configured free cloud model can add synthesis.", true)
            !online -> Route(Mode.LOCAL, "LOCAL • offline Ben", "No validated network: deterministic/local intelligence stays available.", false)
            online && freeAi -> Route(Mode.LOCAL, "LOCAL • Ben first", "Local path is sufficient; Free AI remains available from the Frankenstein menu.", true)
            else -> Route(Mode.LOCAL, "LOCAL • Ben first", "No external free AI provider is configured.", false)
        }
    }

    fun uiSummary(): String {
        val online = hasNetwork()
        val free = gateway.configs().count { it.enabled && it.hasKey }
        return if (online) "🧠 BEN ROUTER • LOCAL FIRST → $free FREE AI PROVIDER${if (free == 1) "" else "S"} READY"
        else "🧠 BEN ROUTER • OFFLINE LOCAL MODE"
    }

    private fun hasNetwork(): Boolean = runCatching {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)
}
