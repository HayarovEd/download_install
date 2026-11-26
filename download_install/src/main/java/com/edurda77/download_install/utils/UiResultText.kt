package com.edurda77.download_install.utils

import android.content.Context
import androidx.annotation.StringRes

sealed class UiResultText {
    data class DynamicString(val value: String) : UiResultText()
    class StringResource(
        @param:StringRes val id: Int,
        val args: Array<Any> = arrayOf(),
    ) : UiResultText()

    fun asString(context: Context): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> context.getString(id, *args)
        }
    }
}
