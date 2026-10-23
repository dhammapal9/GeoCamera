package com.idp.geostampcamera.util

sealed class AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>()
    data class Failure(val throwable: Throwable, val message: String? = null) : AppResult<Nothing>()
}

inline fun <T, R> AppResult<T>.map(block: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(block(value))
    is AppResult.Failure -> this
}
