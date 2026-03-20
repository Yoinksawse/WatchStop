package com.example.watchstop.activities

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.view.screens.MainMapScreen
import com.example.watchstop.view.ui.theme.WatchStopTheme

class MapActivity : AppCompatActivity() {
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WatchStopTheme(darkTheme = UserProfileObject.darkmode) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(
                                    onClick = { finish() },
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                            },
                            title = {
                                Text(
                                    "Create GeoFence",
                                    color = Color.White,
                                    fontSize = MaterialTheme.typography.titleLarge.fontSize * X.value
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = if (UserProfileObject.darkmode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                ) {
                    MainMapScreen()
                }
            }
        }
    }
}
