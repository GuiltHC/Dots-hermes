package app.dotshermes.fork.display

import android.app.Activity
import android.os.Build
import android.util.Log

/**
 * Форк: просит у системы самый быстрый режим экрана (на iQOO 15R — 144 Гц) при текущем разрешении.
 * Без этого прошивка даёт приложению 120 Гц. Прошивка может проигнорировать просьбу — тогда ничего не меняется.
 */
object HighRefresh {
    fun apply(activity: Activity) {
        val display = (if (Build.VERSION.SDK_INT >= 30) activity.display else @Suppress("DEPRECATION") activity.windowManager.defaultDisplay)
            ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        if (best.refreshRate <= current.refreshRate + 0.5f && best.modeId == current.modeId) return
        val attrs = activity.window.attributes
        attrs.preferredDisplayModeId = best.modeId
        activity.window.attributes = attrs
        Log.i("HighRefresh", "preferred mode ${best.modeId}: ${best.refreshRate} Hz (was ${current.refreshRate})")
    }
}
