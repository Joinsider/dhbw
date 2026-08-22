/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.parser.fixtures

/**
 * HTML fixtures for the Dualis parser contract tests.
 *
 * These are Kotlin constants rather than resource files on purpose: reading test resources is
 * awkward across KMP targets (native has no classloader), while a plain string works everywhere.
 *
 * ## Provenance — read this before trusting a fixture
 *
 * - [Documents.LIST] is a **real capture** (`.planning/example/documents.html`, reduced).
 * - Everything else is **derived** from the structures documented in the parser KDocs, which
 *   themselves came from captures (`timetable-week.html`, `lecture-individual.html`).
 *
 * A derived fixture proves the parser handles the shape we believe Dualis emits. It cannot prove
 * that belief is still true. Only a fresh capture does that — so when Dualis changes and a parser
 * breaks in production, replace the fixture with the real page instead of patching the regex.
 */
object DualisFixtures {

    /** Neither HTML nor an error page — the degenerate input every parser must survive. */
    const val EMPTY = ""

    const val NOT_HTML = "totally not html {\"error\": true}"

    /** Dualis answers an expired session with the login form instead of an HTTP status. */
    val SESSION_EXPIRED = """
        <html><head><title>Dualis - Zugang verweigert</title></head>
        <body>
          <div id="pageContent">
            <h1>Ihre Sitzung ist abgelaufen</h1>
            <form action="/scripts/mgrqispi.dll" method="post">
              <input name="usrname" type="text" />
              <input name="pass" type="password" />
              <input type="hidden" name="PRGNAME" value="LOGINCHECK" />
            </form>
          </div>
        </body></html>
    """.trimIndent()

    object Timetable {

        /** Week header plus three appointments; the third is an exam (red background). */
        val WEEK_FULL = """
            <html><body>
            <table class="nb rw-table rw-all">
              <thead>
                <tr>
                  <th class="fixedTimeColumn">Zeit</th>
                  <th class="weekday" abbr="Montag"><a href="#">Mo 03.11.</a></th>
                  <th class="weekday" abbr="Dienstag"><a href="#">Di 04.11.</a></th>
                  <th class="weekday" abbr="Mittwoch"><a href="#">Mi 05.11.</a></th>
                  <th class="weekday" abbr="Donnerstag"><a href="#">Do 06.11.</a></th>
                  <th class="weekday" abbr="Freitag"><a href="#">Fr 07.11.</a></th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="appointment" style="background-color:#FFFFFF;" rowspan="15" abbr="Montag Spalte 1">
                    <span style="font:9px Arial;" class="timePeriod">
                      08:15 - 12:00
                      HOR-120
                    </span>
                    <br />
                    <a href="/scripts/mgrqispi.dll?APPNAME=CampusNet&amp;PRGNAME=COURSEPREP&amp;ARGUMENTS=-N1,-N2,-N3" class="link" title="Paralleles Programmieren  HOR-TINF2024">
                      T4INF2904.1
                    </a>
                  </td>
                  <td class="appointment" style="background-color:#FFFFFF;" rowspan="10" abbr="Mittwoch Spalte 1">
                    <span style="font:9px Arial;" class="timePeriod">
                      09:15 - 10:45
                      HOR-231HOR-232
                    </span>
                    <br />
                    <a href="/scripts/mgrqispi.dll?APPNAME=CampusNet&amp;PRGNAME=COURSEPREP&amp;ARGUMENTS=-N4,-N5,-N6" class="link" title="Form. Sp+Autom.1+2 Gr. B  HOR-TINF2024">
                      T3INF2002.1
                    </a>
                  </td>
                  <td class="appointment" style="background-color:#FF6666;" rowspan="8" abbr="Freitag Spalte 1">
                    <span style="font:9px Arial;" class="timePeriod">
                      14:00 - 15:30
                      HOR-Aula
                    </span>
                    <br />
                    <a href="/scripts/mgrqispi.dll?APPNAME=CampusNet&amp;PRGNAME=COURSEPREP&amp;ARGUMENTS=-N7,-N8,-N9" class="link" title="Klausur Mathematik II  HOR-TINF2024">
                      T3INF1001.2
                    </a>
                  </td>
                </tr>
              </tbody>
            </table>
            </body></html>
        """.trimIndent()

        /** A week with headers but no lectures — semester break, not an error. */
        val WEEK_EMPTY = """
            <html><body>
            <table class="nb rw-table rw-all">
              <thead>
                <tr>
                  <th class="fixedTimeColumn">Zeit</th>
                  <th class="weekday" abbr="Montag"><a href="#">Mo 22.12.</a></th>
                  <th class="weekday" abbr="Dienstag"><a href="#">Di 23.12.</a></th>
                  <th class="weekday" abbr="Mittwoch"><a href="#">Mi 24.12.</a></th>
                  <th class="weekday" abbr="Donnerstag"><a href="#">Do 25.12.</a></th>
                  <th class="weekday" abbr="Freitag"><a href="#">Fr 26.12.</a></th>
                </tr>
              </thead>
              <tbody><tr><td class="tbdata">&nbsp;</td></tr></tbody>
            </table>
            </body></html>
        """.trimIndent()

        /** Appointments present, but the weekday headers are missing — dates cannot be resolved. */
        val WEEK_WITHOUT_HEADERS = """
            <html><body>
            <table class="nb rw-table rw-all">
              <tbody>
                <tr>
                  <td class="appointment" style="background-color:#FFFFFF;" rowspan="15" abbr="Montag Spalte 1">
                    <span class="timePeriod">08:15 - 12:00 HOR-120</span>
                    <br />
                    <a href="/scripts/mgrqispi.dll?ARGUMENTS=-N1" class="link" title="Paralleles Programmieren  HOR-TINF2024">T4INF2904.1</a>
                  </td>
                </tr>
              </tbody>
            </table>
            </body></html>
        """.trimIndent()

        val INDIVIDUAL_PAGE = """
            <html><body>
            <h1>T3INF2002.1&nbsp; Form. Sp+Autom.1+2 Gr. B  HOR-TINF2024</h1>
            <p>
              <span name="appointmentDate">Mi, 5. Nov. 2025</span>&nbsp;
              <span name="appointmentTimeFrom">09:15</span> -
              <span name="appointmentTimeTo">10:45 Uhr</span>
            </p>
            <h2>R&auml;ume:</h2>
            <span name="appoinmentRooms">HOR-231</span>
            <span name="appoinmentRooms">HOR-232</span>
            <table>
              <tr>
                <td class="tbdata" style="text-align:center;" name="instructorName">B.Sc. Julian Schmidt</td>
              </tr>
              <tr>
                <td class="tbdata" style="text-align:center;" name="instructorName">Prof. Dr. Anna M&uuml;ller</td>
              </tr>
            </table>
            </body></html>
        """.trimIndent()

        /** Online lecture: rooms come as links rather than spans. */
        val INDIVIDUAL_PAGE_ROOM_AS_LINK = """
            <html><body>
            <h1>T4INF2904.1&nbsp; Paralleles Programmieren  HOR-TINF2024</h1>
            <a name="appoinmentRooms" href="/scripts/mgrqispi.dll?ARGUMENTS=-N42" class="link">HOR-ONLINE</a>
            <table>
              <tr><td class="tbdata" name="instructorName">Dr. Erika Musterfrau</td></tr>
            </table>
            </body></html>
        """.trimIndent()
    }

    object Grades {

        val SEMESTER_DROPDOWN = """
            <html><body>
            <select name="semester" id="semester">
              <option value="000000015168000" selected="selected">WiSe 2025/26</option>
              <option value="000000015158000">SoSe 2025</option>
              <option value="000000015148000">WiSe 2024/25</option>
            </select>
            </body></html>
        """.trimIndent()

        /**
         * One semester's result table: a graded module, an ungraded one ("noch nicht gesetzt"),
         * a module with a comma decimal in the credits column, and a GPA subhead row that must
         * not be mistaken for a module.
         */
        val SEMESTER_TABLE = """
            <html><body>
            <table class="nb list students_results">
              <thead>
                <tr><th>Nr.</th><th>Modul</th><th>Note</th><th>Credits</th><th>Status</th></tr>
              </thead>
              <tbody>
                <tr>
                  <td class="tbdata">T3INF1001</td>
                  <td class="tbdata">Mathematik I</td>
                  <td class="tbdata">1,7</td>
                  <td class="tbdata">5,0</td>
                  <td class="tbdata">bestanden</td>
                </tr>
                <tr>
                  <td class="tbdata">T3INF2002</td>
                  <td class="tbdata">Formale Sprachen und Automaten</td>
                  <td class="tbdata">noch nicht gesetzt</td>
                  <td class="tbdata">&nbsp;</td>
                  <td class="tbdata">offen</td>
                </tr>
                <tr>
                  <td class="tbdata">T4INF2904</td>
                  <td class="tbdata">Paralleles Programmieren</td>
                  <td class="tbdata">2,3</td>
                  <td class="tbdata">7,5</td>
                  <td class="tbdata">bestanden</td>
                </tr>
                <tr>
                  <td class="tbsubhead">Semester-GPA</td>
                  <td class="tbsubhead">&nbsp;</td>
                  <td class="tbsubhead">2,0</td>
                  <td class="tbsubhead">12,5</td>
                  <td class="tbsubhead">&nbsp;</td>
                </tr>
              </tbody>
            </table>
            </body></html>
        """.trimIndent()

        /** Enrolled but nothing graded yet — a legitimately empty result table. */
        val SEMESTER_TABLE_EMPTY = """
            <html><body>
            <table class="nb list students_results">
              <thead>
                <tr><th>Nr.</th><th>Modul</th><th>Note</th><th>Credits</th><th>Status</th></tr>
              </thead>
              <tbody></tbody>
            </table>
            </body></html>
        """.trimIndent()
    }

    object Documents {

        /** Reduced from the real capture in `.planning/example/documents.html`. */
        val LIST = """
            <html><body>
            <table class="nb list">
              <tbody>
                <tr>
                  <td class="tbdata">
                    <a href="/scripts/mgrqispi.dll?APPNAME=CampusNet&amp;PRGNAME=DOCUMENT&amp;ARGUMENTS=-N1,-N2">
                      Immatrikulationsbescheinigung
                    </a>
                  </td>
                  <td class="tbdata">01.10.2025</td>
                </tr>
              </tbody>
            </table>
            </body></html>
        """.trimIndent()
    }
}
