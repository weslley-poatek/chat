package br.com.weslleycampos.chat.data.auth

import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Provided

/**
 * Firebase Auth on both platforms. `@Provided` because each platform source set binds its own
 * implementation, which the Koin compiler plugin cannot see from common code.
 */
@Provided
interface AuthManager {
    /** The signed-in user, or `null`. Emits the current state as soon as it is collected. */
    val currentUser: Flow<AuthUser?>

    /** Signs in to Firebase with the tokens from the Google account picker. iOS needs [accessToken]. */
    suspend fun signInWithGoogle(idToken: String, accessToken: String? = null)

    fun signOut()
}
