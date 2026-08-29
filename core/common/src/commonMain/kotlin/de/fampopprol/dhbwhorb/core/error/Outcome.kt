/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.core.error

/**
 * The result of an operation that can fail with a known [AppError].
 *
 * Used instead of `kotlin.Result` because SKIE translates a sealed hierarchy into a Swift enum,
 * and because `Result` carries a `Throwable` — which is exactly the untyped failure channel this
 * refactor removes.
 */
sealed interface Outcome<out T> {
    data class Ok<out T>(val value: T) : Outcome<T>
    data class Err(val error: AppError) : Outcome<Nothing>
}

fun <T> T.asOk(): Outcome<T> = Outcome.Ok(this)

fun AppError.asErr(): Outcome<Nothing> = Outcome.Err(this)

val Outcome<*>.isOk: Boolean get() = this is Outcome.Ok

fun <T> Outcome<T>.getOrNull(): T? = when (this) {
    is Outcome.Ok -> value
    is Outcome.Err -> null
}

fun <T> Outcome<T>.errorOrNull(): AppError? = when (this) {
    is Outcome.Ok -> null
    is Outcome.Err -> error
}

fun <T> Outcome<T>.getOrElse(fallback: (AppError) -> @UnsafeVariance T): T = when (this) {
    is Outcome.Ok -> value
    is Outcome.Err -> fallback(error)
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}

inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Ok -> transform(value)
    is Outcome.Err -> this
}

inline fun <T, R> Outcome<T>.fold(onOk: (T) -> R, onErr: (AppError) -> R): R = when (this) {
    is Outcome.Ok -> onOk(value)
    is Outcome.Err -> onErr(error)
}

inline fun <T> Outcome<T>.onOk(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Ok) action(value)
    return this
}

inline fun <T> Outcome<T>.onErr(action: (AppError) -> Unit): Outcome<T> {
    if (this is Outcome.Err) action(error)
    return this
}

/**
 * Recover from a failure with a different outcome — used where a cache can stand in for a failed
 * network call. Deliberately not a blanket `getOrElse(emptyList())`: the caller has to say what
 * the substitute is.
 */
inline fun <T> Outcome<T>.recover(transform: (AppError) -> Outcome<T>): Outcome<T> = when (this) {
    is Outcome.Ok -> this
    is Outcome.Err -> transform(error)
}
