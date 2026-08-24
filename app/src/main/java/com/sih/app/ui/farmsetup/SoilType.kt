package com.sih.app.ui.farmsetup

import androidx.annotation.StringRes
import com.sih.app.R

enum class SoilType(
    @param:StringRes val displayNameRes: Int,
) {
    Sandy(R.string.soil_type_sandy),
    Loamy(R.string.soil_type_loamy),
    Clay(R.string.soil_type_clay),
    Silty(R.string.soil_type_silty),
    RedSoil(R.string.soil_type_red),
    BlackSoil(R.string.soil_type_black),
    Laterite(R.string.soil_type_laterite),
    Other(R.string.soil_type_other),
    Unknown(R.string.soil_type_unknown),
}
