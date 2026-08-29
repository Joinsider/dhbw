/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.core.hash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class Sha256Test {

    private fun hex(text: String) = Sha256.hex(text.encodeToByteArray())

    @Test
    fun theEmptyInputMatchesThePublishedVector() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex(""))
    }

    @Test
    fun theOneBlockVectorMatches() {
        // FIPS 180-4, "abc".
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex("abc"))
    }

    @Test
    fun theTwoBlockVectorMatches() {
        // FIPS 180-4: 56 bytes, which is where the padding needs a second block.
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq")
        )
    }

    @Test
    fun theMillionCharacterVectorMatches() {
        // FIPS 180-4's long message: a million 'a', which is 15625 blocks. A document-sized
        // input, and the case where an error in the block loop finally shows.
        val digest = Sha256.hex(ByteArray(1_000_000) { 'a'.code.toByte() })

        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", digest)
    }

    @Test
    fun exactlyOneBlockIsPaddedIntoASecond() {
        // 64 bytes fills a block exactly, so the marker and the length need a block of their own.
        val digest = Sha256.hex(ByteArray(64) { 0 })

        assertEquals("f5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b", digest)
    }

    @Test
    fun theByteBeforeTheBoundaryStillFits() {
        // 55 bytes is the largest message whose padding fits in one block.
        assertEquals(64, Sha256.hex(ByteArray(55)).length)
        assertEquals(64, Sha256.hex(ByteArray(56)).length)
    }

    @Test
    fun oneChangedByteChangesTheDigest() {
        assertNotEquals(hex("Zeugnis.pdf v1"), hex("Zeugnis.pdf v2"))
    }

    @Test
    fun theSameInputAlwaysGivesTheSameDigest() {
        val bytes = ByteArray(1000) { (it * 7).toByte() }

        assertEquals(Sha256.hex(bytes), Sha256.hex(bytes.copyOf()))
    }
}
