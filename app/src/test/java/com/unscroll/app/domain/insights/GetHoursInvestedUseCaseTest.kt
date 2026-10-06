package com.unscroll.app.domain.insights

import com.unscroll.app.testing.FakeUsageDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetHoursInvestedUseCaseTest {

    @Test
    fun convertsHoursToDaysBooksAndFlights() {
        val invested = GetHoursInvestedUseCase.calculate(48 * HOUR)

        assertEquals(48.0, invested.hours, 1e-9)
        assertEquals(2.0, invested.days, 1e-9)
        assertEquals(8.0, invested.books, 1e-9) // 6 h per book
        assertEquals(4.0, invested.flightsToTokyo, 1e-9) // 12 h per flight
    }

    @Test
    fun usesConstantsFromEquivalents() {
        val invested = GetHoursInvestedUseCase.calculate(90 * MINUTE)
        assertEquals(1.5 / Equivalents.HOURS_PER_BOOK, invested.books, 1e-9)
        assertEquals(1.5 / Equivalents.HOURS_PER_FLIGHT_TO_TOKYO, invested.flightsToTokyo, 1e-9)
        assertEquals(1.5 / Equivalents.HOURS_PER_DAY, invested.days, 1e-9)
    }

    @Test
    fun zeroAndNegative_areZero() {
        assertEquals(0.0, GetHoursInvestedUseCase.calculate(0).days, 0.0)
        assertEquals(0L, GetHoursInvestedUseCase.calculate(-5).totalMillis)
    }

    @Test
    fun invoke_sumsAllTimeAcrossAppsIncludingOpenSession() = runTest {
        val now = at("2026-10-06T20:00:00")
        val source = FakeUsageDataSource(
            mutableListOf(
                session("2025-01-01T10:00:00", "2025-01-01T16:00:00", "com.instagram.android"),
                session("2026-10-06T08:00:00", "2026-10-06T11:00:00", "com.zhiliaoapp.musically"),
                session("2026-10-06T17:00:00", null, "com.facebook.katana"),
            ),
        )

        val invested = GetHoursInvestedUseCase(source)(now)

        assertEquals(12.0, invested.hours, 1e-9)
        assertEquals(2.0, invested.books, 1e-9)
        assertEquals(1.0, invested.flightsToTokyo, 1e-9)
    }
}
