package com.localqbank.library

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Main-process foreground governor for Ben inference.
 *
 * Critical architecture rule: this service owns Android FGS admission. The heavy neural worker
 * remains a normal bound service in :inference_process. Android/OEM foreground-service policy
 * therefore cannot directly tear down or reject the native worker's process as its FGS host.
 */
class BenInferenceForegroundService : Service() {
    private lateinit var messenger: Messenger
    private val leases = ConcurrentHashMap.newKeySet<String>()
    private val ready = AtomicBoolean(false)
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val idleStop = Runnable { if (leases.isEmpty()) stopForegroundAndSelf() }

    override fun onCreate() {
        super.onCreate()
        BenIpcDiagnosticRecorder.event(this, null, "FGS_ON_CREATE")
        messenger = Messenger(Handler(Looper.getMainLooper()) { message -> handle(message) })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ACQUIRE) {
            BenIpcDiagnosticRecorder.event(this, null, "FGS_ON_START_COMMAND", "action=${intent.action}")
            handler.removeCallbacks(idleStop)
            if (!ready.get()) promote()
        }
        return START_NOT_STICKY
    }

    private fun handle(message: Message): Boolean {
        when (message.what) {
            CMD_ACQUIRE -> {
                val lease = message.data.getString(KEY_LEASE_ID).orEmpty()
                val ok = lease.isNotBlank() && ready.get()
                if (ok) { leases.add(lease); BenIpcDiagnosticRecorder.event(this, null, "FGS_LEASE_READY", "lease=${lease.take(8)}") } else BenIpcDiagnosticRecorder.event(this, null, "FGS_LEASE_REJECTED", "notReady")
                message.replyTo?.let { reply ->
                    runCatching {
                        reply.send(Message.obtain(null, REPLY_READY).apply {
                            data = Bundle().apply {
                                putString(KEY_LEASE_ID, lease)
                                putBoolean(KEY_OK, ok)
                                if (!ok) putString(KEY_ERROR, "IPC_FGS_NOT_READY")
                            }
                        })
                    }
                }
            }
            CMD_RELEASE -> {
                message.data.getString(KEY_LEASE_ID)?.let { leases.remove(it) }
                if (leases.isEmpty()) {
                    handler.removeCallbacks(idleStop)
                    handler.postDelayed(idleStop, IDLE_GRACE_MS)
                }
            }
            CMD_READY -> {
                message.replyTo?.let { reply ->
                    runCatching {
                        reply.send(Message.obtain(null, REPLY_READY).apply {
                            data = Bundle().apply { putBoolean(KEY_OK, ready.get()) }
                        })
                    }
                }
            }
        }
        return true
    }

    private fun promote() {
        if (ready.get()) return
        BenIpcDiagnosticRecorder.event(this, null, "FGS_PROMOTION_BEGIN")
        runCatching {
            ensureChannel()
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Ben on-device AI")
                .setContentText("Rovex neural inference is active")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            ready.set(true)
            BenIpcDiagnosticRecorder.event(this, null, "FGS_PROMOTION_PASS")
        }.onFailure { error ->
            ready.set(false)
            BenIpcDiagnosticRecorder.event(this, null, "FGS_PROMOTION_FAIL", "${error.javaClass.simpleName}: ${error.message ?: "unknown"}")
            BenNeuralTelemetry.error("Main-process FGS promotion failed: ${error.javaClass.simpleName}: ${error.message ?: "unknown"}")
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Ben on-device AI", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Foreground protection for active Ben inference"
                }
            )
        }
    }

    private fun stopForegroundAndSelf() {
        ready.set(false)
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        ready.set(false)
        leases.clear()
        handler.removeCallbacks(idleStop)
        super.onDestroy()
    }

    companion object {
        const val ACTION_ACQUIRE = "com.localqbank.library.action.ACQUIRE_BEN_FGS"
        const val CMD_ACQUIRE = 1
        const val CMD_RELEASE = 2
        const val CMD_READY = 3
        const val REPLY_READY = 101
        const val KEY_LEASE_ID = "leaseId"
        const val KEY_OK = "ok"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "ben_inference_foreground"
        private const val NOTIFICATION_ID = 4171
        private const val IDLE_GRACE_MS = 1_500L
    }
}
