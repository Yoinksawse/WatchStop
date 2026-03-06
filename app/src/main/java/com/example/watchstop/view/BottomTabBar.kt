package com.example.watchstop.view

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.view.ui.theme.LocationAlarm
import com.example.watchstop.view.ui.theme.WatchStopTheme


@Composable
fun BottomTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        Pair("Map", Icons.Default.Map),
        Pair("GeoAlarms", Icons.Default.LocationAlarm),
        Pair("Route Tracker", Icons.Default.Route),
        Pair("Groups", Icons.Default.Groups),
    )

    WatchStopTheme(darkTheme = UserProfileObject.darkmode) {
        NavigationBar(
            modifier = Modifier
                .height(100.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            tonalElevation = 8.dp
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = selectedTab == index

                NavigationBarItem(
                    modifier = Modifier.padding(top = 20.dp, bottom = 0.dp),
                    selected = isSelected,
                    onClick = { onTabSelected(index) },
                    label = {
                        Text(
                            text = tab.first,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = tab.second,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
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