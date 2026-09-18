package com.localqbank.library

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class StartupHealthReport(val passed:Int,val failed:Int,val warnings:List<String>,val durationMs:Long){
    val healthy:Boolean get()=failed==0
}

/** Fast, non-destructive launch gate. It validates contracts once per process start; it never
 * performs network calls or cold-starts neural inference. Heavy diagnostics remain on demand. */
class StartupHealthChecker {
    private val activities=listOf("MainActivity","QuizActivity","SettingsActivity","RenActivity","FlashcardActivity","StudyToolsActivity","SearchActivity")
    suspend fun run(context:Context):StartupHealthReport = withContext(Dispatchers.Default){
        ProductionCrashReporter.stage(context, "STARTUP_HEALTH_CHECK", "begin")
        val started=android.os.SystemClock.elapsedRealtime(); var passed=0; var failed=0; val warnings=mutableListOf<String>()
        fun check(ok:Boolean,label:String){if(ok)passed++ else {failed++;warnings+=label}}
        withTimeoutOrNull(900L){
            check(runCatching{AppManagers.initialize(context.applicationContext);AppManagers.isReady()}.getOrDefault(false),"AppManagers")
            check(runCatching{AppManagers.analytics;AppManagers.adaptive;AppManagers.resilienceHealth;AppManagers.battery;AppManagers.flashcardIntelligence;AppManagers.knowledge;AppManagers.frankensteinSupport;AppManagers.cloudAi;AppManagers.frankensteinContext}.isSuccess,"Core managers")
            check(runCatching{BenCloudAiGateway.Provider.values().all{it.endpoint.startsWith("https://") && it.defaultModel.isNotBlank()}}.getOrDefault(false),"AI provider contracts")
            check(runCatching{context.packageManager.getActivityInfo(android.content.ComponentName(context,com.localqbank.library.MainActivity::class.java),0);true}.getOrDefault(false),"MainActivity manifest")
            activities.drop(1).forEach{name->check(runCatching{val cls=Class.forName("com.localqbank.library.$name");context.packageManager.getActivityInfo(android.content.ComponentName(context,cls),0);true}.getOrDefault(false),"$name manifest") }
        } ?: warnings.add("Startup checks reached the 900 ms safety budget")
        val report = StartupHealthReport(passed,failed,warnings,android.os.SystemClock.elapsedRealtime()-started)
        ProductionCrashReporter.stage(context, "STARTUP_HEALTH_CHECK", if (report.healthy) "pass" else "failed=${report.failed}")
        report
    }
}
