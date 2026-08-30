/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.network

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class HttpClientManagerTest {

    private fun client() = HttpClient(MockEngine { respondOk() })

    private val fakeOwner = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = throw NotImplementedError("not used by onDestroy")
    }

    @Test
    fun client_isNullBeforeAnythingIsSet() {
        assertNull(HttpClientManager().client)
    }

    @Test
    fun setClient_makesItTheCurrentClient() = runTest {
        val manager = HttpClientManager()
        val a = client()

        manager.setClient(a)

        assertSame(a, manager.client)
        a.close()
    }

    @Test
    fun setClient_replacingAnActiveClient_closesTheOldOne() = runTest {
        val manager = HttpClientManager()
        val a = client()
        val b = client()
        manager.setClient(a)

        manager.setClient(b)

        assertSame(b, manager.client)
        assertEquals(true, a.coroutineContext.job.isActive.not(), "the replaced client should be closed")
        b.close()
    }

    @Test
    fun setClient_withTheSameInstanceAgain_doesNotCloseIt() = runTest {
        val manager = HttpClientManager()
        val a = client()
        manager.setClient(a)

        manager.setClient(a)

        assertSame(a, manager.client)
        assertEquals(false, a.coroutineContext.job.isActive.not())
        a.close()
    }

    @Test
    fun close_closesTheActiveClientAndClearsIt() = runTest {
        val manager = HttpClientManager()
        val a = client()
        manager.setClient(a)

        manager.close()

        assertNull(manager.client)
        assertEquals(true, a.coroutineContext.job.isActive.not())
    }

    @Test
    fun close_whenNothingWasEverSet_isANoOp() {
        HttpClientManager().close()
    }

    @Test
    fun setClient_afterClose_closesTheNewClientInsteadOfAdoptingIt() = runTest {
        // close() only latches isClosed=true via the branch that has a client to close — so the
        // manager needs one first, otherwise close() on a fresh instance is a no-op (see its body).
        val manager = HttpClientManager()
        manager.setClient(client())
        manager.close()
        val late = client()

        manager.setClient(late)

        assertNull(manager.client)
        assertEquals(true, late.coroutineContext.job.isActive.not())
    }

    @Test
    fun onDestroy_closesTheActiveClient() = runTest {
        val manager = HttpClientManager()
        val a = client()
        manager.setClient(a)

        manager.onDestroy(fakeOwner)

        assertNull(manager.client)
        assertEquals(true, a.coroutineContext.job.isActive.not())
    }
}
