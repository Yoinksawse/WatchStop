package com.example.watchstop.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.watchstop.data.FirebaseRepository
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.model.GroupRole
import com.example.watchstop.model.TripStatus
import com.example.watchstop.view.ui.theme.MapStyles.DARK_MAP_STYLE
import com.example.watchstop.view.ui.theme.WatchStopTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ViewGroupMapActivity : AppCompatActivity() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val groupId = intent.getStringExtra("groupId") ?: ""

        setContent {
            WatchStopTheme(darkTheme = UserProfileObject.darkmode) {
                ViewGroupMapScreen(groupId)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ViewGroupMapScreen(groupId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var groupTitle by remember { mutableStateOf("") }
    var groupMembers by remember { mutableStateOf<List<String>>(emptyList()) }
    var memberRoles by remember { mutableStateOf<Map<String, GroupRole>>(emptyMap()) }
    var locationSharingEnabled by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var tripStatus by remember { mutableStateOf<Map<String, TripStatus>>(emptyMap()) }
    var geofenceCenter by remember { mutableStateOf<LatLng?>(null) }
    var geofenceRadius by remember { mutableStateOf(0.0) }
    var geofencePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var geofenceName by remember { mutableStateOf("") }

    var memberLocations by remember { mutableStateOf<Map<String, Pair<Double, Double>>>(emptyMap()) }
    var memberNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Counter for periodic updates
    var updateTrigger by remember { mutableStateOf(0) }

    val singapore = LatLng(1.3521, 103.8198)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(singapore, 14f)
    }

    val hasLocationPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    // Periodic update trigger - fires every 3 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000L)  // 3 second interval
            updateTrigger++
        }
    }

    // Fetch/refresh group data periodically
    LaunchedEffect(groupId, updateTrigger) {
        try {
            val group = FirebaseRepository.getGroup(groupId)
            if (group != null) {
                groupTitle = group.title
                groupMembers = group.groupMemberNames
                memberRoles = group.memberRoles.toMap()
                locationSharingEnabled = group.locationSharingEnabled.toMap()
                tripStatus = group.tripStatus.toMap()

                // Set geofence data
                group.geofence?.let { geofence ->
                    geofenceCenter = geofence.center
                    geofenceRadius = geofence.radius
                    geofencePoints = geofence.points
                    geofenceName = geofence.name
                }

                // Load member names
                val names = mutableMapOf<String, String>()
                groupMembers.forEach { uid ->
                    names[uid] = FirebaseRepository.getUsername(uid)
                }
                memberNames = names
            }
        } catch (e: Exception) {
            android.util.Log.e("ViewGroupMapActivity", "Error fetching group", e)
        }
    }

    // Subscribe to real-time locations
    LaunchedEffect(groupId) {
        FirebaseRepository.observeGroupLocations(groupId).collect { locations ->
            memberLocations = locations.mapValues { (_, snapshot) ->
                snapshot.lat to snapshot.lng
            }
        }
    }

    // Subscribe to group geofence changes
    LaunchedEffect(groupId) {
        FirebaseRepository.observeGroupGeofence(groupId).collect { geofence ->
            if (geofence != null) {
                geofenceCenter = geofence.center
                geofenceRadius = geofence.radius
                geofencePoints = geofence.points
                geofenceName = geofence.name

                scope.launch {
                    cameraPositionState.animate(
                        update = CameraUpdateFactory.newLatLngZoom(geofence.center, 14f),
                        durationMs = 1000
                    )
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Google Map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapStyleOptions = if (UserProfileObject.darkmode) {
                    MapStyleOptions(DARK_MAP_STYLE)
                } else null
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = hasLocationPermission,
                scrollGesturesEnabled = true,
                zoomGesturesEnabled = true
            )
        ) {
            // Draw Geofence
            geofenceCenter?.let { center ->
                if (geofencePoints.isEmpty()) {
                    // Circular geofence
                    Circle(
                        center = center,
                        radius = geofenceRadius,
                        fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        strokeWidth = 2.5f
                    )
                } else {
                    // Polygonal geofence
                    Polygon(
                        points = geofencePoints,
                        fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        strokeWidth = 2.5f
                    )
                }
            }

            // Draw member location markers
            groupMembers.forEach { memberId ->
                val isSharing = locationSharingEnabled[memberId] ?: false

                if (isSharing) {
                    val location = memberLocations[memberId]
                    val memberName = memberNames[memberId] ?: memberId
                    val memberRole = memberRoles[memberId] ?: GroupRole.MEMBER
                    val memberTripStatus = tripStatus[memberId] ?: TripStatus.INACTIVE

                    location?.let { (lat, lng) ->
                        val markerLatLng = LatLng(lat, lng)

                        // Marker with color based on role
                        val markerColor = when (memberRole) {
                            GroupRole.SUPER_ADMIN -> BitmapDescriptorFactory.HUE_YELLOW
                            GroupRole.ADMIN -> BitmapDescriptorFactory.HUE_BLUE
                            GroupRole.MEMBER -> BitmapDescriptorFactory.HUE_GREEN
                        }

                        Marker(
                            state = MarkerState(position = markerLatLng),
                            title = memberName,
                            snippet = buildMarkerSnippet(memberRole, memberTripStatus),
                            icon = BitmapDescriptorFactory.defaultMarker(markerColor),
                            alpha = if (memberTripStatus == TripStatus.TRAVELLING) 1f else 0.85f
                        )
                    }
                }
            }
        }

        // Legend Card (Top-left)
        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = groupTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (geofenceName.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = geofenceName,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val sharingCount = groupMembers.count { locationSharingEnabled[it] == true }
                Text(
                    text = "Members ($sharingCount/${groupMembers.size})",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Pan to Geofence Button (Top-center)
        if (geofenceCenter != null) {
            IconButton(
                onClick = {
                    scope.launch {
                        cameraPositionState.animate(
                            update = CameraUpdateFactory.newLatLngZoom(geofenceCenter!!, 14f),
                            durationMs = 800
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Pan to Geofence",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Member List Card (Bottom-left)
        Card(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .heightIn(max = 300.dp)
                .widthIn(max = 320.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Active Members",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = if (darkmode) Color.White else Color.Black
                )

                groupMembers.forEach { memberId ->
                    val isSharing = locationSharingEnabled[memberId] ?: false

                    if (isSharing) {
                        val memberName = memberNames[memberId] ?: memberId
                        val memberRole = memberRoles[memberId] ?: GroupRole.MEMBER
                        val memberTripStatus = tripStatus[memberId] ?: TripStatus.INACTIVE

                        MemberListItemInline(
                            name = memberName,
                            role = memberRole,
                            status = memberTripStatus
                        )
                    }
                }

                if (groupMembers.none { locationSharingEnabled[it] == true }) {
                    Text(
                        text = "No members sharing location",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        // Close Button (Top-right)
        IconButton(
            onClick = { (context as? AppCompatActivity)?.finish() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MemberListItemInline(
    name: String,
    role: GroupRole,
    status: TripStatus
) {
    val roleColor = when (role) {
        GroupRole.SUPER_ADMIN -> Color(0xFFFFCC00)
        GroupRole.ADMIN -> Color(0xFF007AFF)
        GroupRole.MEMBER -> Color(0xFF8E8E93)
    }

    val statusColor = when (status) {
        TripStatus.TRAVELLING -> Color(0xFFFF9500)
        TripStatus.ARRIVED -> Color(0xFF34C759)
        TripStatus.INACTIVE -> Color(0xFF8E8E93)
    }

    val statusText = when (status) {
        TripStatus.TRAVELLING -> "Travelling"
        TripStatus.ARRIVED -> "Arrived"
        TripStatus.INACTIVE -> "Inactive"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Name
        Text(
            text = name,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Role Badge
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = roleColor.copy(alpha = 0.2f),
            modifier = Modifier.wrapContentWidth()
        ) {
            Text(
                text = role.displayName,
                fontSize = 8.sp,
                color = roleColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(2.dp, 1.dp)
            )
        }

        // Status Badge
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = statusColor.copy(alpha = 0.2f),
            modifier = Modifier.wrapContentWidth()
        ) {
            Text(
                text = statusText,
                fontSize = 8.sp,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(2.dp, 1.dp)
            )
        }
    }
}

private fun buildMarkerSnippet(role: GroupRole, status: TripStatus): String {
    val roleStr = role.displayName
    val statusStr = when (status) {
        TripStatus.TRAVELLING -> "Travelling"
        TripStatus.ARRIVED -> "Arrived"
        TripStatus.INACTIVE -> "Inactive"
    }
    return "$roleStr • $statusStr"
}