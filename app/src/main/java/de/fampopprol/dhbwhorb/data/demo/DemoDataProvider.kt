package de.fampopprol.dhbwhorb.data.demo

import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DemoDataProvider {

    const val DEMO_USERNAME = "demo@dhbw.de"
    const val DEMO_PASSWORD = "demo123"

    fun isDemoUser(username: String): Boolean {
        return username.equals(DEMO_USERNAME, ignoreCase = true)
    }

    fun getDemoTimetableForWeek(weekStart: LocalDate): List<TimetableDay> {
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val demoTimetable = mutableListOf<TimetableDay>()

        // Generate demo data for Monday to Friday
        for (dayOffset in 0..4) {
            val currentDate = weekStart.plusDays(dayOffset.toLong())
            val dateString = currentDate.format(dateFormatter)

            val events = when (dayOffset) {
                0 -> createMondayEvents() // Monday
                1 -> createTuesdayEvents() // Tuesday
                2 -> createWednesdayEvents() // Wednesday
                3 -> createThursdayEvents() // Thursday
                4 -> createFridayEvents() // Friday
                else -> emptyList()
            }

            demoTimetable.add(
                TimetableDay(
                    date = dateString,
                    events = events
                )
            )
        }

        return demoTimetable
    }

    private fun createMondayEvents(): List<TimetableEvent> {
        return listOf(
            TimetableEvent(
                title = "Software Engineering",
                startTime = "08:00",
                endTime = "09:30",
                room = "A1.2.03",
                lecturer = "Prof. Dr. Schmidt"
            ),
            TimetableEvent(
                title = "Mathematics",
                startTime = "09:45",
                endTime = "11:15",
                room = "A1.2.03",
                lecturer = "Prof. Dr. Müller"
            ),
            TimetableEvent(
                title = "Database Systems",
                startTime = "13:00",
                endTime = "14:30",
                room = "A1.1.15",
                lecturer = "Prof. Dr. Weber"
            )
        )
    }

    private fun createTuesdayEvents(): List<TimetableEvent> {
        return listOf(
            TimetableEvent(
                title = "Computer Networks",
                startTime = "08:00",
                endTime = "09:30",
                room = "A2.1.05",
                lecturer = "Prof. Dr. Fischer"
            ),
            TimetableEvent(
                title = "Project Management",
                startTime = "10:00",
                endTime = "11:30",
                room = "A1.3.12",
                lecturer = "Dr. Wagner"
            ),
            TimetableEvent(
                title = "Web Development Lab",
                startTime = "14:00",
                endTime = "17:00",
                room = "PC-Pool A",
                lecturer = "Prof. Dr. Klein"
            )
        )
    }

    private fun createWednesdayEvents(): List<TimetableEvent> {
        return listOf(
            TimetableEvent(
                title = "Algorithms & Data Structures",
                startTime = "09:00",
                endTime = "10:30",
                room = "A1.2.08",
                lecturer = "Prof. Dr. Hoffmann"
            ),
            TimetableEvent(
                title = "Business Process Management",
                startTime = "11:00",
                endTime = "12:30",
                room = "A2.1.10",
                lecturer = "Prof. Dr. Bauer"
            )
        )
    }

    private fun createThursdayEvents(): List<TimetableEvent> {
        return listOf(
            TimetableEvent(
                title = "Mobile Application Development",
                startTime = "08:30",
                endTime = "10:00",
                room = "A1.3.05",
                lecturer = "Prof. Dr. Richter"
            ),
            TimetableEvent(
                title = "Software Testing",
                startTime = "10:15",
                endTime = "11:45",
                room = "A1.3.05",
                lecturer = "Prof. Dr. Richter"
            ),
            TimetableEvent(
                title = "Seminar Presentation",
                startTime = "13:30",
                endTime = "15:00",
                room = "A2.2.01",
                lecturer = "Prof. Dr. Zimmermann"
            )
        )
    }

    private fun createFridayEvents(): List<TimetableEvent> {
        return listOf(
            TimetableEvent(
                title = "IT Security",
                startTime = "09:00",
                endTime = "10:30",
                room = "A1.1.20",
                lecturer = "Prof. Dr. Schulz"
            ),
            TimetableEvent(
                title = "Practical Training Review",
                startTime = "11:00",
                endTime = "12:30",
                room = "A2.1.03",
                lecturer = "Prof. Dr. Braun"
            ),
            TimetableEvent(
                title = "Notification Test Class",
                startTime = "20:57",
                endTime = "22:30",
                room = "Online",
                lecturer = "Test Lecturer"
            )
        )
    }
}
