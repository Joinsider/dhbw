/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.parser.fixtures

/**
 * Dualis "Ergebnisdetails" pop-ups (`PRGNAME=RESULTDETAILS`), trimmed to the two tables.
 *
 * Both are real pages from a real transcript, with the surrounding chrome — head, scripts,
 * stylesheets, footer — removed and nothing about the tables touched. The template writes the
 * pop-up body wrapper around them, which [HtmlParser.isValidModuleDetailsPage] asks for, so the wrapper is kept.
 */
object ModuleDetailsFixtures {

    private fun popUp(content: String) = """
        <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
        <html><body class="popUpBody">
        <div id="pageContentPopUp" class="pageElementTop">
        $content
        </div>
        </body></html>
    """.trimIndent()

    /** A module with one Baustein and one exam: the ordinary case. */
    val compilerbau: String = popUp(
        """
<form name="courseform">
<h1>
T4INF4211&nbsp;
Compilerbau (SoSe 2026)</h1>
<h2>
 Johannes Popp</h2>
<table class="tb" style="width:700px;">
    <tbody><tr><td class="tbhead" colspan="7">&nbsp;</td></tr>
    <tr>
        <td class="tbsubhead">Versuch</td>
        <td class="tbsubhead">&nbsp;</td>
        <td class="tbsubhead">Prüfung</td>
        <td class="tbsubhead">Datum</td>
        <td class="tbsubhead">Bewertung</td>
        <td class="tbsubhead">Extern anerkannt</td>
        <td class="tbsubhead">&nbsp;</td>
    </tr>
    <tr>
       <td class="level01" colspan="5">
          Versuch  1
       </td>
       <td class="level01">&nbsp;</td>
       <td class="level01">
                     </td>
    </tr>
    <tr>
        <td class="level02" colspan="8">Modulabschlussleistungen</td>
    </tr>
    <tr>
        <td class="tbdata" colspan="2">SoSe 2026</td>
        <td class="tbdata">Klausur oder Kombinierte Prüfung (100%)</td>
        <td class="tbdata" style="vertical-align:top;"></td>
        <td class="tbdata" style="vertical-align:top;">
                                        1,0
                                        </td>
        <td class="tbdata" style="text-align:center;">
        </td>
        <td class="tbdata">
        </td>
    </tr>
    <tr>
        <td class="level02">&nbsp;</td>
        <td class="level02" style="white-space:nowrap;">Gesamt 1</td>
        <td class="level02" colspan="2">&nbsp;</td>
        <td class="level02" style="vertical-align:top;">1,0&nbsp;bestanden</td>
        <td class="level02">&nbsp;</td>
        <td class="level02">&nbsp;</td>
    </tr>
    </tbody></table>
   <h2>Zugehörige Bausteine</h2>
   <table class="tb" style="width:700px;">
       <tbody><tr><td class="tbhead" colspan="4">Pflichtbereich</td></tr>
       <tr>
          <td class="tbsubhead" width="15%">Unit-Nr.</td>
          <td class="tbsubhead" width="25%">Unit-Name</td>
          <td class="tbsubhead" width="40%">Veranstaltung</td>
          <td class="tbsubhead" width="20%">Aktive Teilnahme</td>
        </tr>
       <tr>
           <td class="tbdata" style="vertical-align:top;">T4INF4211.1</td>
           <td class="tbdata" style="vertical-align:top;">Compilerbau</td>
           <td class="tbdata" style="vertical-align:top;">T4INF4211.1 Compilerbau  HOR-TINF2024</td>
           <td class="tbdata" style="text-align:center;">
                         <img src="/img/individual/pass.gif" height="15" alt="Anerkannt" title="Anerkannt">
                </td>
       </tr>
       <tr>
           <td class="tbdata" style="vertical-align:top;">T4INF4211.2</td>
           <td class="tbdata" style="vertical-align:top;">Labor Compilerbau</td>
           <td class="tbdata" style="vertical-align:top;">T4INF4211.2 Labor Compilerbau Gr. 2 HOR-TINF2024</td>
           <td class="tbdata" style="text-align:center;">
                         <img src="/img/individual/pass.gif" height="15" alt="Anerkannt" title="Anerkannt">
                </td>
       </tr>
       </tbody></table>
</form>
        """.trimIndent()
    )

    /**
     * The case that prompted all of this: two attempts, and the second one made of two Bausteine
     * whose grades — 3,6 and 2,8 — are what the module's 3,2 is.
     */
    val mathematikIII: String = popUp(
        """
<form name="courseform">
<h1>
T4INF2001&nbsp;
Mathematik III (WiSe 2025/26)</h1>
<h2>
 Johannes Popp</h2>
<table class="tb" style="width:700px;">
    <tbody><tr><td class="tbhead" colspan="7">&nbsp;</td></tr>
    <tr>
        <td class="tbsubhead">Versuch</td>
        <td class="tbsubhead">&nbsp;</td>
        <td class="tbsubhead">Prüfung</td>
        <td class="tbsubhead">Datum</td>
        <td class="tbsubhead">Bewertung</td>
        <td class="tbsubhead">Extern anerkannt</td>
        <td class="tbsubhead">&nbsp;</td>
    </tr>
    <tr>
       <td class="level01" colspan="5">
          Versuch  1
       </td>
       <td class="level01">&nbsp;</td>
       <td class="level01">
                     </td>
    </tr>
    <tr>
        <td class="level02" colspan="8">T4INF2001.1 Angewandte Mathematik  HOR-TINF2024</td>
    </tr>
    <tr>
        <td class="tbdata" colspan="2">WiSe 2025/26</td>
        <td class="tbdata">Klausur (100%)</td>
        <td class="tbdata" style="vertical-align:top;"></td>
        <td class="tbdata" style="vertical-align:top;">
                                        4,6
                                        </td>
        <td class="tbdata" style="text-align:center;">
        </td>
        <td class="tbdata">
        </td>
    </tr>
    <tr>
        <td class="level02">&nbsp;</td>
        <td class="level02" style="white-space:nowrap;">Gesamt 1</td>
        <td class="level02" colspan="2">&nbsp;</td>
        <td class="level02" style="vertical-align:top;">4,6&nbsp;unvollständig</td>
        <td class="level02">&nbsp;</td>
        <td class="level02">&nbsp;</td>
    </tr>
    <tr>
       <td class="level01" colspan="5">
          Versuch  2
       </td>
       <td class="level01">&nbsp;</td>
       <td class="level01">
                     </td>
    </tr>
    <tr>
        <td class="level02" colspan="8">T4INF2001.1 Angewandte Mathematik  HOR-TINF2024</td>
    </tr>
    <tr>
        <td class="tbdata" colspan="2">WiSe 2025/26</td>
        <td class="tbdata">Klausur (100%)</td>
        <td class="tbdata" style="vertical-align:top;"></td>
        <td class="tbdata" style="vertical-align:top;">
                                        3,6
                                        </td>
        <td class="tbdata" style="text-align:center;">
        </td>
        <td class="tbdata">
        </td>
    </tr>
    <tr>
        <td class="level02" colspan="8">T4INF2001.2 Statistik  HOR-TINF2024</td>
    </tr>
    <tr>
        <td class="tbdata" colspan="2">SoSe 2026</td>
        <td class="tbdata">Klausur (100%)</td>
        <td class="tbdata" style="vertical-align:top;"></td>
        <td class="tbdata" style="vertical-align:top;">
                                        2,8
                                        </td>
        <td class="tbdata" style="text-align:center;">
        </td>
        <td class="tbdata">
        </td>
    </tr>
    <tr>
        <td class="level02">&nbsp;</td>
        <td class="level02" style="white-space:nowrap;">Gesamt 2</td>
        <td class="level02" colspan="2">&nbsp;</td>
        <td class="level02" style="vertical-align:top;">3,2&nbsp;bestanden (Wh.)</td>
        <td class="level02">&nbsp;</td>
        <td class="level02">&nbsp;</td>
    </tr>
    </tbody></table>
   <h2>Zugehörige Bausteine</h2>
   <table class="tb" style="width:700px;">
       <tbody><tr><td class="tbhead" colspan="4">Pflichtbereich</td></tr>
       <tr>
          <td class="tbsubhead" width="15%">Unit-Nr.</td>
          <td class="tbsubhead" width="25%">Unit-Name</td>
          <td class="tbsubhead" width="40%">Veranstaltung</td>
          <td class="tbsubhead" width="20%">Aktive Teilnahme</td>
        </tr>
       <tr>
           <td class="tbdata" style="vertical-align:top;">T4INF2001.1</td>
           <td class="tbdata" style="vertical-align:top;">Angewandte Mathematik</td>
           <td class="tbdata" style="vertical-align:top;">T4INF2001.1 Angewandte Mathematik  HOR-TINF2024</td>
           <td class="tbdata" style="text-align:center;">
                         <img src="/img/individual/pass.gif" height="15" alt="Anerkannt" title="Anerkannt">
                </td>
       </tr>
       <tr>
           <td class="tbdata" style="vertical-align:top;">T4INF2001.2</td>
           <td class="tbdata" style="vertical-align:top;">Statistik</td>
           <td class="tbdata" style="vertical-align:top;">T4INF2001.2 Statistik  HOR-TINF2024</td>
           <td class="tbdata" style="text-align:center;">
                         <img src="/img/individual/pass.gif" height="15" alt="Anerkannt" title="Anerkannt">
                </td>
       </tr>
       </tbody></table>
</form>
        """.trimIndent()
    )
}
