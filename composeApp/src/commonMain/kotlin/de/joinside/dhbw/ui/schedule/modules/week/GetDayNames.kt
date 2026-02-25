package de.joinside.dhbw.ui.schedule.modules.week

import androidx.compose.runtime.Composable
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.friday
import de.joinside.dhbw.resources.friday_short
import de.joinside.dhbw.resources.monday
import de.joinside.dhbw.resources.monday_short
import de.joinside.dhbw.resources.saturday
import de.joinside.dhbw.resources.saturday_short
import de.joinside.dhbw.resources.sunday
import de.joinside.dhbw.resources.sunday_short
import de.joinside.dhbw.resources.thursday
import de.joinside.dhbw.resources.thursday_short
import de.joinside.dhbw.resources.tuesday
import de.joinside.dhbw.resources.tuesday_short
import de.joinside.dhbw.resources.wednesday
import de.joinside.dhbw.resources.wednesday_short
import kotlinx.datetime.DayOfWeek
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(InternalResourceApi::class)
public fun getDayName(dayOfWeek: DayOfWeek, short: Boolean): String {

    if(!short) {
        return when (dayOfWeek) {
            DayOfWeek.MONDAY -> stringResource(Res.string.monday)
            DayOfWeek.TUESDAY -> stringResource(Res.string.tuesday)
            DayOfWeek.WEDNESDAY -> stringResource(Res.string.wednesday)
            DayOfWeek.THURSDAY -> stringResource(Res.string.thursday)
            DayOfWeek.FRIDAY -> stringResource(Res.string.friday)
            DayOfWeek.SATURDAY -> stringResource(Res.string.saturday)
            DayOfWeek.SUNDAY -> stringResource(Res.string.sunday)
        }
    }
    return when (dayOfWeek) {
        DayOfWeek.MONDAY -> stringResource(Res.string.monday_short)
        DayOfWeek.TUESDAY -> stringResource(Res.string.tuesday_short)
        DayOfWeek.WEDNESDAY -> stringResource(Res.string.wednesday_short)
        DayOfWeek.THURSDAY -> stringResource(Res.string.thursday_short)
        DayOfWeek.FRIDAY -> stringResource(Res.string.friday_short)
        DayOfWeek.SATURDAY -> stringResource(Res.string.saturday_short)
        DayOfWeek.SUNDAY -> stringResource(Res.string.sunday_short)
    }
}