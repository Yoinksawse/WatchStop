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
import com.example.watchstop.model.UserProfileObject.darkmode
import com.example.watchstop.view.AssessmentCard
import com.example.watchstop.view.ui.theme.WatchStopTheme

@Composable
fun ModuleInfoScreen() {
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
                            text = "This course introduces students to the design and implementation of" +
                                    "Android applications for mobile devices. Students will develop an App from" +
                                    "scratch, assuming a good knowledge of Java, and learn how to set up" +
                                    "Android Studio, work with various Android building blocks (Activities," +
                                    "Services, Broadcast, etc) to create simple user interfaces to make Apps run" +
                                    "smoothly. At the end of the course, students will learn skills for creating and" +
                                    "deploying Android applications.",
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

            item { AssessmentCard("Lab Work (Best 4 out of 6 Labs)", "2.5% x4 = 10%") }
            item { AssessmentCard("In-Class Assignment 1 (Chapter 2-4)", "15%") }
            item { AssessmentCard("In-Class Assignment 2 (Chapter 6-8)", "20%") }
            item { AssessmentCard("Pairwork Programming Assignment", "15%") }
            item { AssessmentCard("App Development Project", "40%") }

            item { Spacer(modifier = Modifier.height(10.dp)) }
        }
    }
}