package com.cypressit.cyvault.auth

/**
 * Implemented in Swift (e.g. with Amplify) and handed over through [IosAuthBridgeHolder] at
 * app start; [BridgeAuthClient] turns it into an [AuthClient].
 */
interface IosAuthBridge {
    fun signIn(email: String, password: String, completion: (userId: String?, name: String?, error: String?) -> Unit)
    fun register(name: String, email: String, password: String, completion: (error: String?) -> Unit)
    fun confirmSignUp(email: String, code: String, completion: (error: String?) -> Unit)
    fun forgotPassword(email: String, completion: (error: String?) -> Unit)
    fun signOut(completion: (error: String?) -> Unit)
    fun getCurrentUser(completion: (userId: String?, name: String?, email: String?, error: String?) -> Unit)
    fun signInWithGoogle(completion: (userId: String?, name: String?, email: String?, error: String?) -> Unit)
}

object IosAuthBridgeHolder {
    var bridge: IosAuthBridge? = null
}
