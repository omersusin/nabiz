package com.omersusin.nabiz.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore(name = "nabiz")

data class Cal(val offsetMs: Float, val done: Boolean)

/** Tap latency offset, so the player's device is judged on its own terms. */
class Calibration(private val ctx: Context) {
    private val offsetKey = floatPreferencesKey("offset_ms")
    private val doneKey = booleanPreferencesKey("calibrated")

    suspend fun load(): Cal = ctx.store.data
        .map { Cal(it[offsetKey] ?: 0f, it[doneKey] ?: false) }
        .first()

    suspend fun save(offsetMs: Float) {
        ctx.store.edit {
            it[offsetKey] = offsetMs
            it[doneKey] = true
        }
    }
}