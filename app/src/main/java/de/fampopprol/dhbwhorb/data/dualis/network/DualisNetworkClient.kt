/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.network

import android.util.Log
import okhttp3.*
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy

/**
 * Handles HTTP network operations for Dualis communication
 */
class DualisNetworkClient {

    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    val client = OkHttpClient.Builder().cookieJar(JavaNetCookieJar(cookieManager)).build()

    /**
     * Makes an HTTP request and handles common response patterns
     */
    fun makeRequest(
        request: Request,
        requestName: String,
        callback: (Response?, String?) -> Unit
    ) {
        Log.d("DualisNetworkClient", "Making $requestName request to: ${request.url}")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DualisNetworkClient", "$requestName request failed", e)
                callback(null, null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body.string()
                Log.d("DualisNetworkClient", "$requestName Response: ${response.code}")

                if (response.isSuccessful) {
                    callback(response, responseBody)
                } else {
                    Log.e("DualisNetworkClient", "$requestName failed with code: ${response.code}")
                    callback(null, null)
                }
            }
        })
    }

    /**
     * Creates a GET request
     */
    fun createGetRequest(url: String): Request {
        return Request.Builder().url(url).get().build()
    }

    /**
     * Creates a POST request with form data
     */
    fun createPostRequest(url: String, formBody: FormBody): Request {
        return Request.Builder().url(url).post(formBody).build()
    }

    /**
     * Utility method to make absolute URLs
     */
    fun makeAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            val base = java.net.URL(baseUrl)
            java.net.URL(base, relativeUrl).toString()
        } catch (e: Exception) {
            Log.e("DualisNetworkClient", "Error making absolute URL: $e")
            ""
        }
    }
}
