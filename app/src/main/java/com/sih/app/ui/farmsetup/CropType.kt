package com.sih.app.ui.farmsetup

import androidx.annotation.StringRes
import com.sih.app.R

enum class CropType(
    @param:StringRes val displayNameRes: Int,
) {
    Rice(R.string.crop_rice),
    Wheat(R.string.crop_wheat),
    Cotton(R.string.crop_cotton),
    Sugarcane(R.string.crop_sugarcane),
    Maize(R.string.crop_maize),
    Groundnut(R.string.crop_groundnut),
    Soybean(R.string.crop_soybean),
    Pulses(R.string.crop_pulses),
    Tomato(R.string.crop_tomato),
    Onion(R.string.crop_onion),
    Chili(R.string.crop_chili),
    Potato(R.string.crop_potato),
    Other(R.string.crop_other),
}
