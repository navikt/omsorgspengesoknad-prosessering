package no.nav.helse

//Brukes når man logger status i flyten. Formaterer slik at loggen er mer lesbar
internal fun formaterStatuslogging(id: String, melding: String): String {
    return String.format("Søknad med søknadID: %1$36s %2$1s", id, melding)
}