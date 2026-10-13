package com.nora.tunnel.core

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Crash diagnostics that survive process death, so the real reason shows up in Logs on the next launch.
 *  - Java/Kotlin crashes: stack trace saved by an uncaught-exception handler.
 *  - Native crashes (SIGSEGV/abort in hev or the Go runtime): detected via the "stage" breadcrumb
 *    plus Android's recorded process exit reason.
 */
object Diag {
    private const val PREFS = "diag"

    fun install(app: android.app.Application) {
        val file = File(app.filesDir, "last_crash.txt")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { file.writeText("Thread: ${t.name}\n" + Log.getStackTraceString(e)) } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }

    /** Marks what the VPN service is doing. "busy:*" values left behind mean the process died there. */
    fun stage(ctx: Context, s: String) {
        try { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("stage", s).commit() } catch (_: Throwable) {}
    }

    fun report(app: android.app.Application) {
        try {
            val file = File(app.filesDir, "last_crash.txt")
            if (file.exists()) {
                AppLog.add("ERROR", "Previous app crash (Java/Kotlin):")
                file.readText().lines().filter { it.isNotBlank() }.take(14).forEach { AppLog.add("ERROR", it.trim()) }
                file.delete()
            }
            val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stage = sp.getString("stage", "idle").orEmpty()
            if (stage.startsWith("busy:")) {
                AppLog.add("ERROR", "App process died while: ${stage.removePrefix("busy:")}")
            }
            sp.edit().putString("stage", "idle").commit()
            if (Build.VERSION.SDK_INT >= 30) {
                val am = app.getSystemService(ActivityManager::class.java)
                val last = am.getHistoricalProcessExitReasons(app.packageName, 0, 1).firstOrNull()
                if (last != null && stage.startsWith("busy:")) {
                    val why = when (last.reason) {
                        ApplicationExitInfo.REASON_CRASH -> "Java crash"
                        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE crash (hev / Xray library)"
                        ApplicationExitInfo.REASON_SIGNALED -> "killed by signal ${last.status}"
                        ApplicationExitInfo.REASON_LOW_MEMORY -> "killed: low memory"
                        ApplicationExitInfo.REASON_ANR -> "ANR (app not responding)"
                        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "killed: excessive resource usage"
                        else -> "reason code ${last.reason}"
                    }
                    AppLog.add("ERROR", "Android exit reason: $why ${last.description.orEmpty()}")
                }
            }
        } catch (_: Throwable) {}
    }
}
