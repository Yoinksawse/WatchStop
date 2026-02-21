package com.example.watchstop.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import com.example.watchstop.R
import com.example.watchstop.model.UserProfileObject
import com.example.watchstop.view.BottomTabBar
import com.example.watchstop.view.screens.MainMapScreen
import com.example.watchstop.view.screens.ModuleInfoScreen
import com.example.watchstop.view.screens.PlannerScreen
import com.example.watchstop.view.screens.TeacherScreen
import com.example.watchstop.view.ui.theme.WatchStopTheme

var debugOnboardingOn = false; //TODO: just for debugging; TURN OFF

class MainActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // --- Onboarding Logic ---
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

        setContent {
            var darkmodeOn by remember { mutableStateOf(UserProfileObject.darkmode) }

            WatchStopTheme(darkTheme = darkmodeOn) {
                MainScreen(onToggleDarkMode = {
                    UserProfileObject.darkmode = !UserProfileObject.darkmode
                    darkmodeOn = UserProfileObject.darkmode
                })
            }
        }
    }
}


@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onToggleDarkMode: () -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            var expanded by remember { mutableStateOf(false) }

            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = Color.White
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    scrolledContainerColor = MaterialTheme.colorScheme.primary
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
                                    text = { Text("Settings") },
                                    onClick = {
                                        Toast.makeText(context, "Settings clicked", Toast.LENGTH_SHORT).show()
                                        expanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("About WatchStop") },
                                    onClick = {
                                        Toast.makeText(context, "WatchStop v1.0", Toast.LENGTH_SHORT).show()
                                        expanded = false
                                    }
                                )
                            }
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
                1 -> ModuleInfoScreen()
                2 -> TeacherScreen()
                3 -> PlannerScreen()
            }
        }
    }
}
