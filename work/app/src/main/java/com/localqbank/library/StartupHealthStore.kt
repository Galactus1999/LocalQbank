package com.localqbank.library

import android.content.Context

class StartupHealthStore(context:Context){
    private val prefs=context.applicationContext.getSharedPreferences("startup_health",Context.MODE_PRIVATE)
    data class Snapshot(val healthy:Boolean,val failed:Int,val durationMs:Long)
    fun save(report:StartupHealthReport){prefs.edit().putLong("last_ms",report.durationMs).putBoolean("healthy",report.healthy).putInt("failed",report.failed).apply()}
    fun snapshot()=Snapshot(prefs.getBoolean("healthy",true),prefs.getInt("failed",0),prefs.getLong("last_ms",0L))
}
