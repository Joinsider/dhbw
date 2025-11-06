package de.joinside.dhbw.data.dualis.remote.services

import de.joinside.dhbw.data.storage.database.entities.timetable.LectureEventEntity
import kotlinx.datetime.LocalDateTime

object DualisLectureService{

    /**
     * The method returns the weekly lectures for the current week.
     */
    fun getWeeklyLecturesForCurrentWeek(): List<LectureEventEntity>{
        // https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=SCHEDULER&ARGUMENTS=-N818025892738188,-N000028,-A03.11.2025,-A,-N1,-N000000000000000
        // The url above returns a html page which includes the weekly lectures for the current week.
        return emptyList()
    }

    fun getWeeklyLecturesForWeek(start: LocalDateTime, end: LocalDateTime): List<LectureEventEntity> {
        return emptyList();
    }
}