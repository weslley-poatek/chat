package br.com.weslleycampos.chat.data.auth

/**
 * A way to sign in to a Chat account. The app offers only these two, so they are the only ones an
 * account can have here.
 *
 * @property providerId Firebase's id for the provider, as a user's provider data lists it: the
 *   same string on Android (`GoogleAuthProvider.PROVIDER_ID`, `EmailAuthProvider.PROVIDER_ID`) and
 *   iOS (`FIRGoogleAuthProviderID`, `FIREmailAuthProviderID`).
 */
enum class SignInMethod(val providerId: String) {
    GOOGLE("google.com"),
    EMAIL_PASSWORD("password"),
    ;

    companion object {
        /**
         * The methods among Firebase's [providerIds], in this enum's order (Google, then email and
         * password) whatever order they come in, each once. Every other id is left out: Firebase's
         * own `"firebase"` entry, and providers this app never signs in with.
         */
        fun of(providerIds: Iterable<String>): List<SignInMethod> {
            val ids = providerIds.toSet()
            return entries.filter { it.providerId in ids }
        }
    }
}
