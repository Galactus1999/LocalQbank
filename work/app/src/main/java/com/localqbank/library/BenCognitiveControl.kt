package com.localqbank.library

import android.content.Context

/** User-visible switches for Ben's cognitive subsystems. Policy only; no runtime ownership. */
class BenCognitiveControl(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val cognitiveCoreEnabled get() = prefs.getBoolean(KEY_CORE, true)
    val knowledgeGraphEnabled get() = prefs.getBoolean(KEY_GRAPH, true)
    val learnerMemoryEnabled get() = prefs.getBoolean(KEY_LEARNER, true)
    val verifierEnabled get() = prefs.getBoolean(KEY_VERIFIER, true)
    val plannerEnabled get() = prefs.getBoolean(KEY_PLANNER, true)
    val retrievalEnabled get() = prefs.getBoolean(KEY_RETRIEVAL, true)
    val specialistRoutingEnabled get() = prefs.getBoolean(KEY_SPECIALISTS, true)
    val experienceMemoryEnabled get() = prefs.getBoolean(KEY_EXPERIENCE, true)
    val knowledgeIndexEnabled get() = prefs.getBoolean(KEY_INDEX, true)
    val neuralAcceleratorEnabled get() = BenAiRuntimePolicy(appContext).enabled

    fun setCognitiveCoreEnabled(v:Boolean) = prefs.edit().putBoolean(KEY_CORE,v).apply()
    fun setKnowledgeGraphEnabled(v:Boolean) = prefs.edit().putBoolean(KEY_GRAPH,v).apply()
    fun setLearnerMemoryEnabled(v:Boolean) = prefs.edit().putBoolean(KEY_LEARNER,v).apply()
    fun setVerifierEnabled(v:Boolean) = prefs.edit().putBoolean(KEY_VERIFIER,v).apply()
    fun setPlannerEnabled(v:Boolean) = prefs.edit().putBoolean(KEY_PLANNER,v).apply()
    fun setRetrievalEnabled(v:Boolean) = prefs.edit().putBoolean(KEY_RETRIEVAL,v).apply()
    fun setSpecialistRoutingEnabled(v:Boolean) = prefs.edit().putBoolean(KEY_SPECIALISTS,v).apply()
    fun setExperienceMemoryEnabled(v:Boolean) = prefs.edit().putBoolean(KEY_EXPERIENCE,v).apply()
    fun setKnowledgeIndexEnabled(v:Boolean) = prefs.edit().putBoolean(KEY_INDEX,v).apply()

    fun resetToSafeDefaults() {
        prefs.edit().putBoolean(KEY_CORE,true).putBoolean(KEY_GRAPH,true).putBoolean(KEY_LEARNER,true).putBoolean(KEY_VERIFIER,true).putBoolean(KEY_PLANNER,true).putBoolean(KEY_RETRIEVAL,true).putBoolean(KEY_SPECIALISTS,true).putBoolean(KEY_EXPERIENCE,true).putBoolean(KEY_INDEX,true).apply()
        BenAiRuntimePolicy(appContext).forceStop()
    }

    companion object {
        private const val PREFS = "ben_cognitive_control"
        private const val KEY_CORE = "core"
        private const val KEY_GRAPH = "knowledge_graph"
        private const val KEY_LEARNER = "learner_memory"
        private const val KEY_VERIFIER = "verifier"
        private const val KEY_PLANNER = "planner"
        private const val KEY_RETRIEVAL = "retrieval"
        private const val KEY_SPECIALISTS = "specialists"
        private const val KEY_EXPERIENCE = "experience_memory"
        private const val KEY_INDEX = "knowledge_index"
    }
}
