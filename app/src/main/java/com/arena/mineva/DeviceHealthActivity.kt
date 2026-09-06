package com.arena.mineva

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arena.mineva.system.DeviceMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DeviceHealthActivity : AppCompatActivity() {

    private lateinit var body: android.widget.LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.fill(this)
        root.addView(Ui.text(this, "💾 سلامت دستگاه", 22f, 0xFFC97C22.toInt(), bold = true))
        body = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }
        root.addView(body)

        status = Ui.button(this, "🔄 بهروزرسانی", 0xFF2E9BFF.toInt(), 46f)
        status.setOnClickListener { refresh() }
        root.addView(status)
        setContentView(root)
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            body.removeAllViews()
            body.addView(Ui.text(this@DeviceHealthActivity, "در حال اندازهگیری...", 14f, 0xFF9FB2C2.toInt()))
            delay(350)
            val s = DeviceMonitor.snapshot(this@DeviceHealthActivity)
            body.removeAllViews()
            body.addView(metric("رم کل", "${s.totalRamMb} MB", Color.WHITE))
            body.addView(metric("رم آزاد", "${s.freeRamMb} MB", 0xFF35D07F.toInt()))
            body.addView(metric("رم استفادهشده", "${s.usedRamMb} MB", 0xFF2E9BFF.toInt()))
            body.addView(metric("حافظه کل", "${s.totalStorageMb} MB", Color.WHITE))
            body.addView(metric("حافظه آزاد", "${s.freeStorageMb} MB", 0xFF35D07F.toInt()))
            body.addView(metric("دمای باتری", if (s.temperatureC > 0) "${s.temperatureC}°C" else "نامشخص", if (s.temperatureC > 45f) 0xFFFF5A5A.toInt() else Color.WHITE))
            body.addView(metric("بار CPU", "${s.cpuLoadPercent}%", Color.WHITE))
            body.addView(metric("کش اپ", "${s.cacheMb} MB", 0xFF9FB2C2.toInt()))
            body.addView(
                Ui.button(this@DeviceHealthActivity, "🧹 پاکسازی کش", 0xFF1F8F8F.toInt(), 46f) {
                    DeviceMonitor.clearAppCache(this@DeviceHealthActivity)
                    status.setText("کش پاک شد ✓")
                    refresh()
                }
            )
        }
    }

    private fun metric(name: String, value: String, color: Int): android.view.View {
        val row = Ui.horizontal(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        row.addView(Ui.text(this, name, 14f, 0xFF9FB2C2.toInt(), gravity = android.view.Gravity.START))
        val v = Ui.text(this, value, 16f, color, bold = true, gravity = android.view.Gravity.END)
        val lp = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        v.layoutParams = lp
        row.addView(v)
        return row
    }
}
