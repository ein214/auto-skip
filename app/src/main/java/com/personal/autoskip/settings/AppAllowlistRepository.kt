package com.personal.autoskip.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 사용자가 고른 앱에서만 동작한다. 기본값은 빈 목록(어디서도 동작하지 않음). */
class AppAllowlistRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    val allowed: Flow<Set<String>> = store.data.map { prefs ->
        (prefs[KEY_ALLOWED] ?: emptySet()).filterNot { isExcluded(it) }.toSet()
    }

    suspend fun setAllowed(packageName: String, allowed: Boolean) {
        if (allowed && isExcluded(packageName)) return
        store.edit { prefs ->
            val current = prefs[KEY_ALLOWED] ?: emptySet()
            prefs[KEY_ALLOWED] = if (allowed) current + packageName else current - packageName
        }
    }

    companion object {
        private val KEY_ALLOWED = stringSetPreferencesKey("allowed_packages")

        /**
         * 허용 목록에 넣을 수 없는 민감 앱. 스토어/설정/시스템 UI/설치 관리자/권한 화면에서는
         * 절대 클릭하지 않는다.
         */
        val ALWAYS_EXCLUDED = setOf(
            "com.personal.autoskip",
            "com.android.vending",
            "com.android.settings",
            "com.android.systemui",
            "com.google.android.gms",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        )

        fun isExcluded(packageName: String): Boolean = packageName in ALWAYS_EXCLUDED
    }
}
