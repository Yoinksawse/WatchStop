package com.example.watchstop.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.example.watchstop.R
import com.example.watchstop.data.GeoAlarmsDatabase
import com.example.watchstop.data.UserGeofencesDatabase
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.service.GeofenceMonitorService
import com.example.watchstop.view.BottomTabBar
import com.example.watchstop.view.screens.MainMapScreen
import com.example.watchstop.view.screens.GeoAlarmsScreen
import com.example.watchstop.view.screens.RouteTrackerScreen
import com.example.watchstop.view.screens.GroupsScreen
import com.example.watchstop.view.ui.theme.WatchStopTheme
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.watchstop.view.ui.theme.LocationAlarm

var debugOnboardingOn = true; //TODO: just for debugging; TURN OFF

class MainActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen() //INSTALL SPLASH SCREEN

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var ready = false
        splashScreen.setKeepOnScreenCondition { !ready } //PERSIST SPLASHSCREEN

        //load saved data from cache
        UserGeofencesDatabase.loadGeofencesFromCache(this)
        GeoAlarmsDatabase.loadAlarmsFromCache(this)

        //auto login & other info gathering logic
        val userPrefs = getSharedPreferences("WatchStopUserPrefs", MODE_PRIVATE)
        //userPrefs.edit().clear().apply() //TODO: debug line; clear cache manually

        //save creds if just return from successful login/signup
        val intentIdentifier = intent.getStringExtra("LOGIN_IDENTIFIER")
        val intentPassword = intent.getStringExtra("LOGIN_PASSWORD")
        if (intentIdentifier != null && intentPassword != null) {
            userPrefs.edit()
                .putString("currentLoggedInAccount", intentIdentifier)
                .putString("savedPassword", intentPassword)
                .apply()
        }

        //auto sign in if lastsignedinuser field is not null
        val savedEmail = userPrefs.getString("currentLoggedInAccount", "")
        val savedPassword = userPrefs.getString("savedPassword", "")
        if (!savedEmail.isNullOrEmpty() && !savedPassword.isNullOrEmpty() && !UserProfileObject.isLoggedIn) {
            lifecycleScope.launch {
                try {
                    UserProfileObject.signIn(savedEmail, savedPassword)
                    UserProfileObject.syncFromFirebase()

                    //merge data from firebase + upload merged data to firebase
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

        //ONBOARDING SCREEN
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

        ready = true //STOP SPLASH SCREEN

        setContent {
            WatchStopTheme(darkTheme = UserProfileObject.darkmode) {
                val context = LocalContext.current

                val foregroundLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val granted = permissions.values.all { it }
                    if (granted) {
                        checkAndRequestBackgroundPermission()
                    }
                }

                LaunchedEffect(Unit) {
                    val foregroundGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (foregroundGranted) {
                        checkAndRequestBackgroundPermission()
                    } else {
                        foregroundLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                        }
                    }
                }

                MainScreen(onToggleDarkMode = {
                    UserProfileObject.darkmode = !UserProfileObject.darkmode
                    UserProfileObject.pushToFirebase()
                })
            }
        }
    }

    private val backgroundLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startGeofenceService()
        } else {
            Toast.makeText(this, "Set Location to 'Allow all the time' in settings for background alarms", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndRequestBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasBackground = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
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
}


@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onToggleDarkMode: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            var expanded by remember { mutableStateOf(false) }

            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.LocationAlarm,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            color = Color.White
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (UserProfileObject.darkmode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    scrolledContainerColor = if (UserProfileObject.darkmode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                ),
                actions = {
                    Row {
                        IconButton(onClick = {
                            onToggleDarkMode()
                        }) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                tint = Color.White,
                                contentDescription = "Toggle Dark Mode"
                            )
                        }

                        Box {
                            IconButton(onClick = { expanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    tint = Color.White,
                                    contentDescription = "Menu"
                                )
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Show Tutorial") },
                                    onClick = {
                                        val intent = Intent(context, OnboardingActivity::class.java)
                                        context.startActivity(intent)
                                        expanded = false
                                    }
                                )
                                //IMPLICIT INTENT USAGE TO OPEN WEBSITE: FULFILLED!!!!
                                DropdownMenuItem(
                                    text = { Text("About WatchStop") },
                                    onClick = {
                                        val watchStopGithubIntent = Intent(
                                            Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://github.com/Yoinksawse/WatchStop")
                                        )
                                        context.startActivity(watchStopGithubIntent)
                                        expanded = false
                                    }
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (UserProfileObject.isLoggedIn) {
                                    val intent = Intent(context, ProfileActivity::class.java)
                                    context.startActivity(intent)
                                }
                                else {
                                    val intent = Intent(context, LoginActivity::class.java)
                                    context.startActivity(intent)
                                }
                            }
                        ) {
                            Image(
                                painter = painterResource(R.drawable.defaultpfp),
                                contentDescription = "Profile",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomTabBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> MainMapScreen()
                1 -> GeoAlarmsScreen(
                    onRequestMap = {
                        selectedTab = 0
                    },
                )
                2 -> RouteTrackerScreen()
                3 -> GroupsScreen()
            }
        }
    }
}
