package com.example.watchstop.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.watchstop.data.GeoAlarmsDatabase
import com.example.watchstop.data.UserGeofencesDatabase
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.service.GeofenceMonitorService
import com.example.watchstop.view.screens.MainScreen
import com.example.watchstop.view.ui.theme.WatchStopTheme
import kotlinx.coroutines.launch

var debugOnboardingOn = false
var X = mutableFloatStateOf(1.0f)

class MainActivity : AppCompatActivity() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var ready = false
        splashScreen.setKeepOnScreenCondition { !ready }

        // Load saved data from cache
        UserGeofencesDatabase.loadGeofencesFromCache(this)
        GeoAlarmsDatabase.loadAlarmsFromCache(this)

        // Auto login & other info gathering logic
        val userPrefs = getSharedPreferences("WatchStopUserPrefs", MODE_PRIVATE)

        // Save creds if just returned from successful login/signup
        val intentIdentifier = intent.getStringExtra("LOGIN_IDENTIFIER")
        val intentPassword = intent.getStringExtra("LOGIN_PASSWORD")
        if (intentIdentifier != null && intentPassword != null) {
            userPrefs.edit()
                .putString("currentLoggedInAccount", intentIdentifier)
                .putString("savedPassword", intentPassword)
                .apply()
        }

        // Auto sign in if saved credentials exist
        val savedEmail = userPrefs.getString("currentLoggedInAccount", "")
        val savedPassword = userPrefs.getString("savedPassword", "")
        if (!savedEmail.isNullOrEmpty() && !savedPassword.isNullOrEmpty() && !UserProfileObject.isLoggedIn) {
            lifecycleScope.launch {
                try {
                    UserProfileObject.signIn(savedEmail, savedPassword)
                    UserProfileObject.syncFromFirebase()
                    UserGeofencesDatabase.fetchGeofencesFromFirebaseDB()
                    GeoAlarmsDatabase.fetchAlarmsFromFirebaseDB()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Auto sign-in failed: ${e.message}")
                    userPrefs.edit()
                        .remove("currentLoggedInAccount")
                        .remove("savedPassword")
                        .apply()
                }
            }
        } else {
            UserProfileObject.syncFromFirebase()
            if (UserProfileObject.isLoggedIn) {
                UserGeofencesDatabase.fetchGeofencesFromFirebaseDB()
                GeoAlarmsDatabase.fetchAlarmsFromFirebaseDB()
            }
        }

        // Onboarding screen
        val prefs = getSharedPreferences("lab4_prefs", MODE_PRIVATE)
        if (debugOnboardingOn) {
            prefs.edit().putBoolean("first_use", true).apply()
            debugOnboardingOn = false
        }
        if (prefs.getBoolean("first_use", true)) {
            val onboardingIntent = Intent(this, OnboardingActivity::class.java)
            startActivity(onboardingIntent)
            prefs.edit { putBoolean("first_use", false) }
        }

        ready = true

        // Request battery optimization exemption so service isn't deferred
        requestBatteryOptimizationExemption()
        openOemBatterySettings()

        // Request notification permission before setContent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Start location permission chain immediately, not deferred inside LaunchedEffect
        val foregroundGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (foregroundGranted) {
            checkAndRequestBackgroundPermission()
        } else {
            foregroundPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        setContent {
            WatchStopTheme(darkTheme = UserProfileObject.darkmode) {
                MainScreen(onToggleDarkMode = {
                    UserProfileObject.darkmode = !UserProfileObject.darkmode
                    UserProfileObject.pushToFirebase()
                })
            }
        }
    }

    private val foregroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            checkAndRequestBackgroundPermission()
        } else {
            Toast.makeText(
                this,
                "Location permission is required for geofence monitoring",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val backgroundLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startGeofenceService()
        } else {
            Toast.makeText(
                this,
                "Set Location to 'Allow all the time' in settings for background alarms",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun checkAndRequestBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasBackground = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (hasBackground) {
                startGeofenceService()
            } else {
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        } else {
            startGeofenceService()
        }
    }

    private fun startGeofenceService() {
        Log.d("MainActivity", "Starting Geofence Monitor Service")
        val serviceIntent = Intent(this, GeofenceMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun openOemBatterySettings() {
        val prefs = getSharedPreferences("WatchStopUserPrefs", MODE_PRIVATE)
        if (prefs.getBoolean("oem_battery_prompted", false)) return

        val oemIntents = listOf(
            // Samsung
            Intent().setComponent(android.content.ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity")),
            // Xiaomi
            Intent().setComponent(android.content.ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity")),
            // Oppo / Realme
            Intent().setComponent(android.content.ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.FakeActivity")),
            // Huawei
            Intent().setComponent(android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            // Generic fallback
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )
        for (intent in oemIntents) {
            try {
                startActivity(intent)
                prefs.edit { putBoolean("oem_battery_prompted", true) }
                return
            } catch (_: Exception) {}
        }
    }
}