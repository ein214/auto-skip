package com.personal.autoskip

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.personal.autoskip.settings.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 빠른 설정 타일: 어느 화면에서든 알림창을 내려 즉시 켜고 끌 수 있는 긴급 중지 스위치. */
class AutoSkipTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs by lazy { PreferencesRepository(this) }

    override fun onStartListening() {
        scope.launch { render(prefs.enabled.first()) }
    }

    override fun onClick() {
        scope.launch {
            val next = !prefs.enabled.first()
            prefs.setEnabled(next)
            render(next)
        }
    }

    private fun render(enabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "AutoSkip"
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
