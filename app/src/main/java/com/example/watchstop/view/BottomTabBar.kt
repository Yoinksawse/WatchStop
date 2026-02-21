package com.example.watchstop.view

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.watchstop.model.UserProfileObject
import com.example.watchstop.view.ui.theme.WatchStopTheme


@Composable
fun BottomTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        Pair("Map", Icons.Default.Map),
        Pair("GeoAlarms", Icons.Default.LocationAlarm),
        Pair("Groups", Icons.Default.Groups),
        Pair("Map History", Icons.Default.Route),
    )

    WatchStopTheme(darkTheme = UserProfileObject.darkmode) {
        NavigationBar(
            modifier = androidx.compose.ui.Modifier.height(100.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            tonalElevation = 8.dp
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = selectedTab == index

                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelected(index) },
                    label = {
                        Text(
                            text = tab.first,
                            // Increased font size from labelMedium to titleSmall or bodyMedium
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = tab.second,
                            contentDescription = null,
                            // Manually set icon size (default is 24.dp)
                            modifier = androidx.compose.ui.Modifier.size(32.dp)
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}