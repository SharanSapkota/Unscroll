package com.unscroll.app.domain.device

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceManufacturerTest {

    @Test
    fun knownManufacturers_areCaseInsensitive() {
        assertEquals(DeviceManufacturer.XIAOMI, DeviceManufacturer.from("Xiaomi"))
        assertEquals(DeviceManufacturer.XIAOMI, DeviceManufacturer.from("Redmi"))
        assertEquals(DeviceManufacturer.XIAOMI, DeviceManufacturer.from("POCO"))
        assertEquals(DeviceManufacturer.SAMSUNG, DeviceManufacturer.from("samsung"))
        assertEquals(DeviceManufacturer.HUAWEI, DeviceManufacturer.from("HUAWEI"))
        assertEquals(DeviceManufacturer.HUAWEI, DeviceManufacturer.from("HONOR"))
        assertEquals(DeviceManufacturer.ONEPLUS, DeviceManufacturer.from("OnePlus"))
    }

    @Test
    fun unknownBlankOrNull_isOther() {
        assertEquals(DeviceManufacturer.OTHER, DeviceManufacturer.from("Google"))
        assertEquals(DeviceManufacturer.OTHER, DeviceManufacturer.from(""))
        assertEquals(DeviceManufacturer.OTHER, DeviceManufacturer.from(null))
    }

    @Test
    fun surroundingWhitespace_isIgnored() {
        assertEquals(DeviceManufacturer.SAMSUNG, DeviceManufacturer.from(" samsung "))
    }
}
