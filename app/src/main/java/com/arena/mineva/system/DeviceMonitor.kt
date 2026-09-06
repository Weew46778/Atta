package com.arena.mineva.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import java.io.File

data class DeviceSnapshot(
    val totalRamMb: Long,
    val freeRamMb: Long,
    val usedRamMb: Long,
    val totalStorageMb: Long,
    val freeStorageMb: Long,
    val temperatureC: Float,
    val cpuLoadPercent: Int,
    val cacheMb: Long
)

object DeviceMonitor {

    fun snapshot(context: Context): DeviceSnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val totalRam = mi.totalMem / (1024 * 1024)
        val freeRam = mi.availMem / (1024 * 1024)
        val usedRam = (totalRam - freeRam).coerceAtLeast(0)

        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val totalStorage = stat.totalBytes / (1024 * 1024)
        val freeStorage = stat.availableBytes / (1024 * 1024)

        val temp = batteryTemperatureC(context)
        val cpu = cpuLoadPercent(PushStats())
        val cache = cacheSizeMb(context)

        return DeviceSnapshot(
            totalRamMb = totalRam,
            freeRamMb = freeRam,
            usedRamMb = usedRam,
            totalStorageMb = totalStorage,
            freeStorageMb = freeStorage,
            temperatureC = temp,
            cpuLoadPercent = cpu,
            cacheMb = cache
        )
    }

    fun batteryTemperatureC(context: Context): Float {
        val intent: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val t = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        return if (t <= 0) -1f else t / 10f
    }

    fun clearAppCache(context: Context): Long {
        val before = cacheSizeMb(context)
        cacheDir(context).listFiles()?.forEach { it.deleteRecursively() }
        return before
    }

    private fun cacheDir(context: Context): File = context.cacheDir

    private fun cacheSizeMb(context: Context): Long {
        fun size(f: File): Long = if (f.isDirectory) f.listFiles().orEmpty().sumOf { size(it) } else f.length()
        return size(cacheDir(context)) / (1024 * 1024)
    }

    private class PushStats {
        val cpu = File("/proc/stat").readText()
        val time = System.nanoTime()
    }

    private fun cpuLoadPercent(start: PushStats): Int {
        return try {
            val a = start.cpu.split("\n").firstOrNull()?.trim()?.split("\\s+".toRegex())
                ?.mapNotNull { it.toLongOrNull() } ?: return 0
            val end = File("/proc/stat").readText()
            val b = end.split("\n").firstOrNull()?.trim()?.split("\\s+".toRegex())
                ?.mapNotNull { it.toLongOrNull() } ?: return 0
            val idleA = a.getOrElse(3) { 0 } + a.getOrElse(4) { 0 }
            val idleB = b.getOrElse(3) { 0 } + b.getOrElse(4) { 0 }
            val idle = idleB - idleA
            val totalB = b.sum()
            val totalA = a.sum()
            val total = totalB - totalA
            if (total <= 0) 0 else ((total - idle) * 100 / total).toInt().coerceIn(0, 100)
        } catch (_: Exception) {
            0
        }
    }
}
