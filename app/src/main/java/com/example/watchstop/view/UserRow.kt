package com.example.watchstop.view

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.view.ui.theme.WatchStopTheme

@Composable
fun UserRow(userName: String, role: Int) {
    WatchStopTheme (darkTheme = UserProfileObject.darkmode) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = userName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
            Text(
                text = when (role) {
                        0 -> "Super Admin"
                        1 -> "Admin"
                        else -> "Member"
                    },
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}