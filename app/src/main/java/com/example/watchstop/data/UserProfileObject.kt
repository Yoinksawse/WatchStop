package com.example.watchstop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

const val GUEST_USERNAME = "User"
const val GUEST_EMAIL = "a@b.c"
const val DEFAULT_PFP = "defaultpfp"
const val INITIAL_DARKMODE = true

// Firebase auth + realtime database used to store most data, but this is temp holder
object UserProfileObject {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var profileJob: Job? = null

    // ====================== Compose-observable state ====================
    var userName: String by mutableStateOf(GUEST_USERNAME)
    var userPfpReference: String by mutableStateOf(DEFAULT_PFP)
    var darkmode: Boolean by mutableStateOf(INITIAL_DARKMODE)
    var email: String by mutableStateOf(GUEST_EMAIL)


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
        email = GUEST_EMAIL
        userPfpReference = DEFAULT_PFP
        isLoggedIn = false
        darkmode = true
    }

    // ======================= Sync from Firebase =========================

    private fun startObservingProfile(uid: String) {
        profileJob?.cancel()
        profileJob = scope.launch {
            FirebaseRepository.observeUserProfile(uid).collect { profile ->
                if (profile != null) {
                    userName = profile.userName
                    email = profile.email
                    userPfpReference = profile.userPfpReference
                    darkmode = profile.darkmode
                    // isLoggedIn is true ONLY if the account is not named "Guest"
                    isLoggedIn = !userName.equals(GUEST_USERNAME, ignoreCase = true)
                }
            }
        }
    }

    private fun stopObservingProfile() {
        profileJob?.cancel()
        profileJob = null
    }

    //manually sync if needed (e.g. on startup)
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
                data = UserProfileData(
                    userName = userName,
                    email = email,
                    userPfpReference = userPfpReference,
                    darkmode = darkmode
                )
            )
        }
    }

    // ========================= Auth helpers =============================

    suspend fun signIn(email: String, password: String) {
        val firebaseUser = FirebaseRepository.signIn(email, password)
        this.email = firebaseUser.email ?: email
        
        // UPLOAD THE EXISTING GEOALARMS FIRST
        // We push local guest data to the newly signed-in account immediately
        GeoAlarmsDatabase.updateAlarmsToFirebaseDB()
        UserGeofencesDatabase.updateGeofencesToFirebaseDB()
        
        this.isLoggedIn = true
    }

    suspend fun signUp(email: String, password: String, userName: String) {
        FirebaseRepository.signUp(email, password, userName)
        
        // UPLOAD THE EXISTING GEOALARMS FIRST
        GeoAlarmsDatabase.updateAlarmsToFirebaseDB()
        UserGeofencesDatabase.updateGeofencesToFirebaseDB()
        
        this.email = email
        this.userName = userName
        this.userPfpReference = DEFAULT_PFP
        this.darkmode = INITIAL_DARKMODE
        this.isLoggedIn = true
    }

    fun signOut() {
        FirebaseRepository.signOut()
        isLoggedIn = false
    }
}
