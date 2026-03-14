package com.example.watchstop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

const val GUEST_USERNAME = "Guest"
const val GUEST_EMAIL = "a@b.c"
const val DEFAULT_PFP = "defaultpfp"
const val INITIAL_DARKMODE = true

/**
 * In-memory state for the currently signed-in user.
 *
 * Source of truth is now Firebase Auth + Realtime Database.
 */
object UserProfileObject {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var profileJob: Job? = null

    // ── Compose-observable state ──────────────────────────────────────────
    var userName: String by mutableStateOf(GUEST_USERNAME)
    var userPfpReference: String by mutableStateOf(DEFAULT_PFP)
    var darkmode: Boolean by mutableStateOf(INITIAL_DARKMODE)
    var email: String by mutableStateOf(GUEST_EMAIL)

    /** Reactive login state for Compose. 
     *  True only if the user is authenticated AND has a non-Guest username.
     */
    var isLoggedIn: Boolean by mutableStateOf(false)
        private set

    val uid: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    init {
        // Automatically sync when auth state changes
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                // We have an auth session, start fetching profile to check Guest status
                startObservingProfile(user.uid)
            } else {
                stopObservingProfile()
                resetToGuest()
            }
        }
    }

    private fun resetToGuest() {
        userName = GUEST_USERNAME
        userPfpReference = DEFAULT_PFP
        isLoggedIn = false
        darkmode = true
    }

    // ── Sync from Firebase ────────────────────────────────────────────────

    private fun startObservingProfile(uid: String) {
        profileJob?.cancel()
        profileJob = scope.launch {
            FirebaseRepository.observeUserProfile(uid).collect { profile ->
                if (profile != null) {
                    userName = profile.userName
                    userPfpReference = profile.userPfpReference
                    darkmode = profile.darkmode
                    // isLoggedIn is true ONLY if the account is not named "Guest"
                    isLoggedIn = !userName.equals(GUEST_USERNAME, ignoreCase = true)
                } else {
                    // If no profile found, treat as Guest until one is created
                    userName = GUEST_USERNAME
                    isLoggedIn = false
                }
            }
        }
    }

    private fun stopObservingProfile() {
        profileJob?.cancel()
        profileJob = null
    }

    /** Manually trigger a sync if needed (e.g. on app startup). */
    fun syncFromFirebase(externalScope: CoroutineScope = CoroutineScope(Dispatchers.Main)) {
        val currentUid = uid ?: return
        startObservingProfile(currentUid)
    }

    fun pushToFirebase(externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        val currentUid = uid ?: return
        // Do not allow Guest accounts to persist changes to the backend
        if (!isLoggedIn) return 
        
        externalScope.launch {
            FirebaseRepository.saveUserProfile(
                uid = currentUid,
                data = UserProfileData(
                    userName = userName,
                    userPfpReference = userPfpReference,
                    darkmode = darkmode
                )
            )
        }
    }

    // ── Auth helpers ──────────────────────────────────────────────────────

    suspend fun signIn(email: String, password: String) {
        val firebaseUser = FirebaseRepository.signIn(email, password)
        this.email = firebaseUser.email.let{email.substringBefore("@")}
        this.userName = firebaseUser.email.let{email.substringBefore("@")}
        this.isLoggedIn = true
    }

    suspend fun signUp(email: String, password: String, userName: String) {
        FirebaseRepository.signUp(email, password, userName)
        signIn(email, password) //DO NOT REMOVE

        this.userName = userName.ifEmpty { email.substringBefore("@") }
        this.userPfpReference = DEFAULT_PFP
        this.darkmode = INITIAL_DARKMODE
        this.isLoggedIn = true
    }

    fun signOut() {
        FirebaseRepository.signOut()
        isLoggedIn = false
    }

    fun inDarkMode() = darkmode
    fun setDarkMode(value: Boolean) {
        darkmode = value
        pushToFirebase()
    }
}
