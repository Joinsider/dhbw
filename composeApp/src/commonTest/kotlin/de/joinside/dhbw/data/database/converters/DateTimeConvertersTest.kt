package de.joinside.dhbw.data.database.converters

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DateTimeConvertersTest {

    private val converter = DateTimeConverter()

    @Test
    fun testToLocalDate_withValidString_returnsLocalDateTime() {
        // Given
        val dateString = "2023-10-15T14:30:00"

        // When
        val result = converter.toLocalDate(dateString)

        // Then
        assertEquals(LocalDateTime.parse(dateString), result)
    }

    @Test
    fun testToLocalDate_withNull_returnsNull() {
        // Given
        val dateString: String? = null

        // When
        val result = converter.toLocalDate(dateString)

        // Then
        assertNull(result)
    }

    @Test
    fun testFromLocalDate_withValidLocalDateTime_returnsString() {
        // Given
        val dateTime = LocalDateTime.parse("2023-10-15T14:30:00")

        // When
        val result = converter.fromLocalDate(dateTime)

        // Then
        assertEquals("2023-10-15T14:30:00", result)
    }

    @Test
    fun testFromLocalDate_withNull_returnsNull() {
        // Given
        val dateTime: LocalDateTime? = null

        // When
        val result = converter.fromLocalDate(dateTime)

        // Then
        assertNull(result)
    }

    @Test
    fun testRoundTrip_convertsBackAndForth() {
        // Given
        val originalDateTime = LocalDateTime.parse("2023-12-25T09:15:30")

        // When
        val stringResult = converter.fromLocalDate(originalDateTime)
        val dateTimeResult = converter.toLocalDate(stringResult)

        // Then
        assertEquals(originalDateTime, dateTimeResult)
    }
}