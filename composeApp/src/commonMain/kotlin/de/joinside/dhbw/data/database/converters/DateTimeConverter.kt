package de.joinside.dhbw.data.database.converters

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class DateTimeConverter {
    @TypeConverter
    fun toLocalDate(dateString: String?): LocalDateTime? {
        return dateString?.let { LocalDateTime.Companion.parse(it) }
    }

    @TypeConverter
    fun fromLocalDate(date: LocalDateTime?): String? {
        return date?.toString()
    }
}