package com.example.watchstop.view.screens

import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.watchstop.R
import com.example.watchstop.activities.LoginActivity
import com.example.watchstop.activities.OnboardingActivity
import com.example.watchstop.activities.ProfileActivity
import com.example.watchstop.activities.X
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.view.BottomTabBar
import com.example.watchstop.view.ui.theme.LocationAlarm

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
                            color = Color.White,
                            fontSize = MaterialTheme.typography.titleLarge.fontSize * X.value
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
                        IconButton(onClick = { onToggleDarkMode() }) {
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
                                    text = {
                                        Text(
                                            "Show Tutorial",
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * X.value
                                        )
                                    },
                                    onClick = {
                                        val intent = Intent(context, OnboardingActivity::class.java)
                                        context.startActivity(intent)
                                        expanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "About Us/Contact Us",
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * X.value
                                        )
                                    },
                                    onClick = {
                                        val watchStopGithubIntent = Intent(
                                            Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://github.com/Yoinksawse/WatchStop")
                                        )
                                        context.startActivity(watchStopGithubIntent)
                                        expanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (X.floatValue == 1.3f) "Toggle Small Font" else "Toggle Large Font",
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * X.value
                                        )
                                    },
                                    onClick = {
                                        if (X.floatValue == 1.0f) X.floatValue = 1.3f else X.floatValue = 1.0f
                                    }
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (UserProfileObject.isLoggedIn) {
                                    val intent = Intent(context, ProfileActivity::class.java)
                                    context.startActivity(intent)
                                } else {
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
                    onRequestMap = { selectedTab = 0 }
                )
                2 -> RouteTrackerScreen()
                3 -> GroupsScreen()
            }
        }
    }
}