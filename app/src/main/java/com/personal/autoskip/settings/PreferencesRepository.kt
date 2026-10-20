package com.personal.autoskip.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 프로세스당 하나의 DataStore 인스턴스만 존재해야 하므로 top-level 확장 프로퍼티로 둔다.
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    /** 앱 내 마스터 스위치. 접근성 서비스가 켜져 있어도 이 값이 false면 아무 동작도 하지 않는다. */
    val enabled: Flow<Boolean> = store.data.map { it[KEY_ENABLED] ?: false }

    suspend fun setEnabled(value: Boolean) {
        store.edit { it[KEY_ENABLED] = value }
    }

    /** 광고 쿨타임이 끝나면 알림. 기본 켜짐. */
    val cooldownAlert: Flow<Boolean> = store.data.map { it[KEY_COOLDOWN_ALERT] ?: true }

    suspend fun setCooldownAlert(value: Boolean) {
        store.edit { it[KEY_COOLDOWN_ALERT] = value }
    }

    /** 자동 종료 후 호스트 복귀까지 확인된 횟수. */
    val closedCount: Flow<Int> = store.data.map { it[KEY_CLOSED_COUNT] ?: 0 }

    suspend fun incrementClosedCount() {
        store.edit { it[KEY_CLOSED_COUNT] = (it[KEY_CLOSED_COUNT] ?: 0) + 1 }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("enabled")
        val KEY_CLOSED_COUNT = intPreferencesKey("closed_count")
        val KEY_COOLDOWN_ALERT = booleanPreferencesKey("cooldown_alert")
    }
}
