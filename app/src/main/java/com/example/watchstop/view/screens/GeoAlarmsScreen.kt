package com.example.watchstop.view.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.view.GeoAlarmCard
import com.example.watchstop.view.ui.theme.WatchStopTheme

@Composable
fun GeoAlarmsScreen() {
    WatchStopTheme (darkTheme = darkmode) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor =
                            if (darkmode) Color(0xFF1E1E1E)
                            else Color.White
                    )
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 20.dp)) {
                        Text(
                            text = "CS4131 Mobile Application Development",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (darkmode) Color.White else Color.Black
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "",
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            color = if (darkmode) Color.LightGray else Color.DarkGray
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Mode of Assessment",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (darkmode) Color.White else Color.Black
                )
            }

            item { GeoAlarmCard("Lab Work (Best 4 out of 6 Labs)", "2.5% x4 = 10%") }
            item { GeoAlarmCard("In-Class Assignment 1 (Chapter 2-4)", "15%") }
            item { GeoAlarmCard("In-Class Assignment 2 (Chapter 6-8)", "20%") }
            item { GeoAlarmCard("Pairwork Programming Assignment", "15%") }
            item { GeoAlarmCard("App Development Project", "40%") }

            item { Spacer(modifier = Modifier.height(10.dp)) }
        }
    }
}