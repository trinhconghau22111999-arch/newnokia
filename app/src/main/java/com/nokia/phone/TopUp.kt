package com.nokia.phone

import android.content.Context

/** Mã đầu nạp thẻ (mặc định *100*), đổi bằng lệnh giọng nói "đổi mã nạp thẻ thành ...". */
object TopUpCode {
    const val DEFAULT = "*100*"
    private const val PREF = "topup"
    private const val KEY = "prefix"

    fun get(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, DEFAULT) ?: DEFAULT

    fun set(ctx: Context, prefix: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, prefix).apply()
    }
}
