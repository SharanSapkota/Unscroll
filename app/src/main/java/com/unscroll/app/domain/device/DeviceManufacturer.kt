package com.unscroll.app.domain.device

/** Manufacturers whose battery savers are known to kill background apps aggressively. */
enum class DeviceManufacturer {
    XIAOMI,
    SAMSUNG,
    HUAWEI,
    ONEPLUS,
    OTHER,
    ;

    companion object {
        /** Maps `Build.MANUFACTURER` (or `Build.BRAND`) to a known manufacturer. */
        fun from(manufacturer: String?): DeviceManufacturer =
            when (manufacturer?.trim()?.lowercase()) {
                "xiaomi", "redmi", "poco" -> XIAOMI
                "samsung" -> SAMSUNG
                "huawei", "honor" -> HUAWEI
                "oneplus" -> ONEPLUS
                else -> OTHER
            }
    }
}
