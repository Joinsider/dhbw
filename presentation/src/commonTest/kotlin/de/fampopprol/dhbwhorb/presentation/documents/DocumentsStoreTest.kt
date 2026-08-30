/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.documents

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.domain.usecase.DownloadDocument
import de.fampopprol.dhbwhorb.domain.usecase.ListDocuments
import de.fampopprol.dhbwhorb.presentation.TestScopes
import de.fampopprol.dhbwhorb.presentation.collectEffects
import de.fampopprol.dhbwhorb.testutil.fakes.FakeDocumentRepository
import de.fampopprol.dhbwhorb.testutil.fakes.FakeSessionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentsStoreTest {

    private val certificate = DualisDocument(
        title = "Studienbescheinigung",
        date = "25.03.26",
        time = "09:40",
        downloadUrl = "/scripts/filetransfer.exe?cert"
    )
    private val payment = DualisDocument(
        title = "Zahlungsinformation Semesterbeiträge",
        date = "19.02.26",
        time = "14:47",
        downloadUrl = "/scripts/filetransfer.exe?pay"
    )

    private fun store(
        documents: FakeDocumentRepository,
        session: FakeSessionRepository = FakeSessionRepository(canAuthenticate = true)
    ) = DocumentsStore(
        listDocuments = ListDocuments(documents),
        downloadDocument = DownloadDocument(documents),
        sessionRepository = session,
        scope = TestScopes.immediate()
    )

    @Test
    fun loading_fillsTheList() = runTest {
        val store = store(FakeDocumentRepository(documents = Outcome.Ok(listOf(certificate, payment))))

        store.dispatch(DocumentsIntent.Load)

        assertEquals(2, store.state.value.documents.size)
        assertFalse(store.state.value.isLoading)
        store.close()
    }

    @Test
    fun reEnteringTheScreen_doesNotRefetch() = runTest {
        var listCalls = 0
        val repository = object : FakeDocumentRepository(documents = Outcome.Ok(listOf(certificate))) {
            override suspend fun listDocuments(): Outcome<List<DualisDocument>> {
                listCalls++
                return super.listDocuments()
            }
        }
        val store = store(repository)

        store.dispatch(DocumentsIntent.EnsureLoaded)
        store.dispatch(DocumentsIntent.EnsureLoaded)

        assertEquals(1, listCalls, "Coming back to the tab must cost nothing")
        store.close()
    }

    @Test
    fun anEmptyDocumentList_stillCountsAsLoaded() = runTest {
        var listCalls = 0
        val repository = object : FakeDocumentRepository(documents = Outcome.Ok(emptyList())) {
            override suspend fun listDocuments(): Outcome<List<DualisDocument>> {
                listCalls++
                return super.listDocuments()
            }
        }
        val store = store(repository)

        store.dispatch(DocumentsIntent.EnsureLoaded)
        store.dispatch(DocumentsIntent.EnsureLoaded)

        // A student with no documents has an empty list, not an unloaded one.
        assertEquals(1, listCalls)
        store.close()
    }

    @Test
    fun searching_filtersWithoutReloading() = runTest {
        val repository = FakeDocumentRepository(documents = Outcome.Ok(listOf(certificate, payment)))
        val store = store(repository)

        store.dispatch(DocumentsIntent.Load)
        store.dispatch(DocumentsIntent.SearchChanged("zahlung"))

        assertEquals(listOf(payment), store.state.value.documents)

        // Widening the search again must not need another request: the unfiltered list is kept.
        store.dispatch(DocumentsIntent.SearchChanged(""))
        assertEquals(2, store.state.value.documents.size)
        store.close()
    }

    @Test
    fun openingADocument_handsTheBytesToThePlatform() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val repository = FakeDocumentRepository(
            documents = Outcome.Ok(listOf(certificate)),
            download = Outcome.Ok(bytes)
        )
        val store = store(repository)
        val effects = mutableListOf<DocumentsEffect>()
        val collector = collectEffects(store) { effects += it }

        store.dispatch(DocumentsIntent.Open(certificate))

        // Opening a file is a platform call, so it leaves the store as an effect rather than
        // being made from inside it.
        assertEquals(
            listOf<DocumentsEffect>(DocumentsEffect.OpenFile("Studienbescheinigung.pdf", bytes)),
            effects
        )
        collector.cancel()
        store.close()
    }

    @Test
    fun savingADocument_asksForTheSaveDialogInstead() = runTest {
        val bytes = byteArrayOf(9)
        val repository = FakeDocumentRepository(download = Outcome.Ok(bytes))
        val store = store(repository)
        val effects = mutableListOf<DocumentsEffect>()
        val collector = collectEffects(store) { effects += it }

        store.dispatch(DocumentsIntent.Save(certificate))

        assertEquals(
            listOf<DocumentsEffect>(DocumentsEffect.SaveFile("Studienbescheinigung.pdf", bytes)),
            effects
        )
        collector.cancel()
        store.close()
    }

    @Test
    fun aFinishedDownload_clearsItsBusyMarker() = runTest {
        val repository = FakeDocumentRepository(download = Outcome.Ok(byteArrayOf(1)))
        val store = store(repository)

        store.dispatch(DocumentsIntent.Open(certificate))

        assertFalse(
            store.state.value.isDownloading(certificate),
            "A finished download must not leave the row spinning"
        )
        store.close()
    }

    @Test
    fun aFailedDownload_isReportedOnceAndClearsTheMarker() = runTest {
        val repository = FakeDocumentRepository(download = Outcome.Err(AppError.Offline))
        val store = store(repository)
        val effects = mutableListOf<DocumentsEffect>()
        val collector = collectEffects(store) { effects += it }

        store.dispatch(DocumentsIntent.Open(certificate))

        assertEquals(listOf<DocumentsEffect>(DocumentsEffect.DownloadFailed(AppError.Offline)), effects)
        assertFalse(store.state.value.isDownloading(certificate))
        collector.cancel()
        store.close()
    }

    @Test
    fun demoMode_saysDownloadsAreUnavailableRatherThanFailing() = runTest {
        val repository = FakeDocumentRepository(
            download = Outcome.Err(AppError.Unsupported("Documents cannot be downloaded in demo mode"))
        )
        val store = store(repository)

        store.dispatch(DocumentsIntent.Open(certificate))

        assertTrue(store.state.value.error is AppError.Unsupported)
        store.close()
    }

    @Test
    fun withoutCredentials_itAsksForALoginRatherThanShowingAnError() = runTest {
        val store = store(FakeDocumentRepository(), FakeSessionRepository(canAuthenticate = false))

        store.dispatch(DocumentsIntent.Load)

        val state = store.state.value
        assertTrue(state.requiresLogin, "Without a session the user must be asked to log in")
        assertNull(state.error, "A missing session is not an error condition")
        assertFalse(state.isLoading, "Loading has to finish")
        store.close()
    }

    @Test
    fun refreshing_alsoFillsTheListAndClearsRefreshing() = runTest {
        val store = store(FakeDocumentRepository(documents = Outcome.Ok(listOf(certificate))))

        store.dispatch(DocumentsIntent.Refresh)

        assertEquals(listOf(certificate), store.state.value.documents)
        assertFalse(store.state.value.isRefreshing)
        store.close()
    }

    @Test
    fun aFailedList_isReportedAsAnError() = runTest {
        val store = store(FakeDocumentRepository(documents = Outcome.Err(AppError.Offline)))

        store.dispatch(DocumentsIntent.Load)

        assertEquals(AppError.Offline, store.state.value.error)
        assertFalse(store.state.value.isLoading)
        store.close()
    }

    @Test
    fun reduceDocuments_aFailureWithNoCredentials_alsoAsksForLogin() {
        val failed = reduceDocuments(DocumentsState(), DocumentsMsg.Failed(AppError.NoCredentials))

        assertEquals(AppError.NoCredentials, failed.error)
        assertTrue(failed.requiresLogin)
    }

    @Test
    fun reduceDocuments_anyOtherFailure_doesNotAskForLogin() {
        val failed = reduceDocuments(DocumentsState(), DocumentsMsg.Failed(AppError.Offline))

        assertEquals(AppError.Offline, failed.error)
        assertFalse(failed.requiresLogin)
    }

    @Test
    fun twoDocumentsSharingATitle_areTrackedSeparately() = runTest {
        val second = certificate.copy(date = "07.11.25", time = "13:26")
        val repository = FakeDocumentRepository(download = Outcome.Ok(byteArrayOf(1)))
        val store = store(repository)

        // Dualis reissues the same certificate, so title alone cannot identify a row.
        assertFalse(certificate.key() == second.key())
        store.dispatch(DocumentsIntent.Open(second))
        assertFalse(store.state.value.isDownloading(certificate))
        store.close()
    }
}
