package no.nav.helse.felles

internal class HttpError(httpStatus : Int, message: String) : Throwable("HTTP $httpStatus -> $message")