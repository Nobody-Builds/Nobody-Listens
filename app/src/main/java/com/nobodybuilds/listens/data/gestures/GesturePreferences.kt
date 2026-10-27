package com.nobodybuilds.listens.data.gestures

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.gesturesDataStore: DataStore<Preferences> by preferencesDataStore(name = "lmusic_gestures")

data class GestureSettings(
    val shakeToShuffle: Boolean = false,
    val faceDownToPause: Boolean = false,
    val resumeWhenFaceUp: Boolean = false,
    val doubleTapToLike: Boolean = true,
    val shakeSensitivity: ShakeSensitivity = ShakeSensitivity.MEDIUM,
) {
    val anySensorGesture: Boolean get() = shakeToShuffle || faceDownToPause
}

/** Settings › Behavior › Gestures. Sensor gestures default off; double-tap like defaults on. */
@Singleton
class GesturePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val SHAKE = booleanPreferencesKey("shake_to_shuffle")
        val FACE_DOWN = booleanPreferencesKey("face_down_to_pause")
        val RESUME_FACE_UP = booleanPreferencesKey("resume_when_face_up")
        val DOUBLE_TAP_LIKE = booleanPreferencesKey("double_tap_to_like")
        val SENSITIVITY = stringPreferencesKey("shake_sensitivity")
    }

    val settings: Flow<GestureSettings> = context.gesturesDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { p ->
            GestureSettings(
                shakeToShuffle = p[Keys.SHAKE] ?: false,
                faceDownToPause = p[Keys.FACE_DOWN] ?: false,
                resumeWhenFaceUp = p[Keys.RESUME_FACE_UP] ?: false,
                doubleTapToLike = p[Keys.DOUBLE_TAP_LIKE] ?: true,
                shakeSensitivity = p[Keys.SENSITIVITY]
                    ?.let { raw -> ShakeSensitivity.entries.firstOrNull { it.name == raw } }
                    ?: ShakeSensitivity.MEDIUM,
            )
        }
        .distinctUntilChanged()

    val doubleTapToLike: Flow<Boolean> = settings.map { it.doubleTapToLike }.distinctUntilChanged()

    suspend fun setShakeToShuffle(enabled: Boolean) = context.gesturesDataStore.edit { it[Keys.SHAKE] = enabled }
    suspend fun setFaceDownToPause(enabled: Boolean) = context.gesturesDataStore.edit { it[Keys.FACE_DOWN] = enabled }
    suspend fun setResumeWhenFaceUp(enabled: Boolean) = context.gesturesDataStore.edit { it[Keys.RESUME_FACE_UP] = enabled }
    suspend fun setDoubleTapToLike(enabled: Boolean) = context.gesturesDataStore.edit { it[Keys.DOUBLE_TAP_LIKE] = enabled }
    suspend fun setShakeSensitivity(value: ShakeSensitivity) =
        context.gesturesDataStore.edit { it[Keys.SENSITIVITY] = value.name }
}

/** Same DataStore for the backup module (data/backup/module/FeatureDataStores.kt). */
internal val Context.backupGesturesDataStore: DataStore<Preferences> get() = gesturesDataStore
