/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.domain.usecase.DownloadDocument
import de.fampopprol.dhbwhorb.domain.usecase.ListDocuments
import de.fampopprol.dhbwhorb.presentation.TestScopes
import de.fampopprol.dhbwhorb.presentation.documents.DocumentsStore
import de.fampopprol.dhbwhorb.testutil.fakes.FakeDocumentRepository
import de.fampopprol.dhbwhorb.testutil.fakes.FakeSessionRepository
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavItem
import de.fampopprol.dhbwhorb.ui.navigation.navItemTestTag
import kotlin.test.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalTestApi::class)
class DocumentsPageTest {

    private fun document(title: String) = DualisDocument(
        title = title,
        date = "01.03.26",
        time = "12:00",
        downloadUrl = "/scripts/filetransfer.exe?$title",
    )

    private fun store(
        documents: List<DualisDocument> = emptyList(),
        canAuthenticate: Boolean = true,
    ): DocumentsStore {
        val repository = FakeDocumentRepository(documents = Outcome.Ok(documents))
        return DocumentsStore(
            listDocuments = ListDocuments(repository),
            downloadDocument = DownloadDocument(repository),
            sessionRepository = FakeSessionRepository(canAuthenticate = canAuthenticate),
            scope = TestScopes.immediate(),
        )
    }

    @Test
    fun requiresLogin_showsLoginMessageInsteadOfDocuments() = runComposeUiTest {
        setContent { DocumentsPage(store = store(canAuthenticate = false)) }
        waitForIdle()

        onNodeWithText("Please log in to view your documents").assertIsDisplayed()
    }

    @Test
    fun noDocuments_showsEmptyMessage() = runComposeUiTest {
        setContent { DocumentsPage(store = store(documents = emptyList())) }
        waitForIdle()

        onNodeWithText("No documents available.").assertIsDisplayed()
    }

    @Test
    fun withDocuments_showsThemAndBottomNavigation() = runComposeUiTest {
        setContent { DocumentsPage(store = store(documents = listOf(document("Studienbescheinigung")))) }
        waitForIdle()

        onNodeWithText("Studienbescheinigung").assertIsDisplayed()
        onNodeWithTag(navItemTestTag(BottomNavItem.DOCUMENTS)).assertIsDisplayed()
    }

    @Test
    fun searching_filtersTheList() = runComposeUiTest {
        setContent {
            DocumentsPage(
                store = store(documents = listOf(document("Studienbescheinigung"), document("Zahlungsinformation")))
            )
        }
        waitForIdle()

        onNodeWithText("Search Documents").performTextInput("Studien")
        waitForIdle()

        onNodeWithText("Studienbescheinigung").assertIsDisplayed()
        assertFailsWith<AssertionError> { onNodeWithText("Zahlungsinformation").assertIsDisplayed() }
    }

    @Test
    fun searchWithNoMatches_showsSearchSpecificEmptyMessage() = runComposeUiTest {
        setContent { DocumentsPage(store = store(documents = listOf(document("Studienbescheinigung")))) }
        waitForIdle()

        onNodeWithText("Search Documents").performTextInput("nonexistent")
        waitForIdle()

        onNodeWithText("No documents found matching your search.").assertIsDisplayed()
    }

    @Test
    fun clearingTheSearch_removesTheFilter() = runComposeUiTest {
        setContent { DocumentsPage(store = store(documents = listOf(document("Studienbescheinigung")))) }
        waitForIdle()

        onNodeWithText("Search Documents").performTextInput("nonexistent")
        waitForIdle()
        onNodeWithContentDescription("Clear Search").performClick()
        waitForIdle()

        onNodeWithText("Studienbescheinigung").assertIsDisplayed()
    }
}
