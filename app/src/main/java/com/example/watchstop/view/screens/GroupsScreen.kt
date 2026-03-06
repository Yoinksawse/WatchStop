package com.example.watchstop.view.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.view.UserRow
import com.example.watchstop.view.ui.theme.WatchStopTheme
import kotlinx.coroutines.launch

@Composable
fun GroupsScreen() {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    WatchStopTheme (darkTheme = darkmode) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text("Email Mr Chua") },
                    icon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Email sent to Mr Chua")
                        }
                    },
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor =
                            if (darkmode) Color(0xFF1E1E1E)
                            else Color(0xFFF5F5F5)
                    ),
                    modifier = Modifier.fillMaxWidth(0.9f),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {

                        Text(
                            text = "Teacher Contact",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (darkmode) Color.White else Color.Black
                        )

                        HorizontalDivider()

                        Text(
                            text = "My Group",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (darkmode) Color.White else Color.Black
                        )

                        UserRow("Yeoh Jun De", 2)
                        UserRow("Moodra Sampeng", 1)
                    }
                }
            }
        }
    }
}