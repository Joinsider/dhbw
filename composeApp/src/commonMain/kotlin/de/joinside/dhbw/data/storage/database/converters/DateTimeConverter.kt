package de.joinside.dhbw.data.storage.database.converters

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number

class DateTimeConverter {
    @TypeConverter
    fun toLocalDate(dateString: String?): LocalDateTime? {
        return dateString?.let { LocalDateTime.parse(it) }
    }

    @TypeConverter
    fun fromLocalDate(date: LocalDateTime?): String? {
        return date?.let {
            // Format to ISO-8601 with full precision: yyyy-MM-ddTHH:mm:ss
            "${it.year.toString().padStart(4, '0')}-" +
                    "${it.month.number.toString().padStart(2, '0')}-" +
                    "${it.day.toString().padStart(2, '0')}T" +
                    "${it.hour.toString().padStart(2, '0')}:" +
                    "${it.minute.toString().padStart(2, '0')}:" +
                    it.second.toString().padStart(2, '0')
        }
    }
}