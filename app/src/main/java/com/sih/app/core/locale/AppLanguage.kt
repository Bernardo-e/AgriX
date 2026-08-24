package com.sih.app.core.locale

import androidx.annotation.StringRes
import com.sih.app.R

enum class AppLanguage(
    val tag: String,
    @StringRes val displayNameRes: Int,
) {
    English("en", R.string.language_name_english),
    Tamil("ta", R.string.language_name_tamil),
    Hindi("hi", R.string.language_name_hindi),
    Telugu("te", R.string.language_name_telugu),
    Kannada("kn", R.string.language_name_kannada),
    Malayalam("ml", R.string.language_name_malayalam),
    ;

    companion object {
        val Default = English

        fun fromTag(tag: String): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: Default
    }
}
