package com.sih.app.ui.farmsetup

import androidx.annotation.StringRes
import com.sih.app.R

enum class FarmAreaUnit(
    @param:StringRes val displayNameRes: Int,
) {
    Acres(R.string.farm_setup_unit_acres),
    Hectares(R.string.farm_setup_unit_hectares),
}
