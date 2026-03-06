package com.example.watchstop.view

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.view.ui.theme.WatchStopTheme

@Composable
fun GeoAlarmCard(title: String, weight: String) {
    WatchStopTheme (darkTheme = darkmode) {
        Card(
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(4.dp),
            colors = CardDefaults.cardColors(
                containerColor =
                    if (darkmode) Color(0xFF262626)
                    else Color(0xFFF7F7F7)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    color = if (darkmode) Color.White else Color.Black
                )

                VerticalDivider(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .height(20.dp),
                    color = if (darkmode) Color.Gray else Color.DarkGray,
                    thickness = 1.dp
                )

                Text(
                    text = weight,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (darkmode) Color.White else Color.Black
                )
            }
        }
    }
}