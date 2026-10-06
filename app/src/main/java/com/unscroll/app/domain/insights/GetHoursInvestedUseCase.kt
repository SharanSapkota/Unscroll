package com.unscroll.app.domain.insights

import javax.inject.Inject

/** All-time usage expressed as days of life, books and flights to Tokyo. */
class GetHoursInvestedUseCase @Inject constructor(
    private val source: UsageDataSource,
) {
    suspend operator fun invoke(now: Long): HoursInvested =
        calculate(source.appTotals(TimeRange(0L, Long.MAX_VALUE), now).values.sum())

    companion object {
        fun calculate(totalMillis: Long): HoursInvested {
            val hours = totalMillis.coerceAtLeast(0) / Equivalents.MILLIS_PER_HOUR
            return HoursInvested(
                totalMillis = totalMillis.coerceAtLeast(0),
                hours = hours,
                days = hours / Equivalents.HOURS_PER_DAY,
                books = hours / Equivalents.HOURS_PER_BOOK,
                flightsToTokyo = hours / Equivalents.HOURS_PER_FLIGHT_TO_TOKYO,
            )
        }
    }
}
