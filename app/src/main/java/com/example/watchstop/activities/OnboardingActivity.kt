package com.example.watchstop.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchstop.model.ShapeState
import com.example.watchstop.view.ui.theme.WatchStopTheme
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.painterResource
import com.example.watchstop.R

class OnboardingActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val darkmode: Boolean = isSystemInDarkTheme()
            WatchStopTheme (darkTheme = darkmode){
                Scaffold(
                    topBar = {}
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        OnboardingReactive(darkmode) { finish() }
                    }
                }
            }
        }
    }
}

//animated background stuff below yay!
@Composable
fun OnboardingReactive(
    darkmode: Boolean,
    onFinish: () -> Unit
) {
    val items = listOf(
        OnboardingItem(
            "Please ensure a strong cellular/WiFi connection (preferably GPS as well) for the app to function at its peak!"
        ),

        OnboardingItem(
            "Welcome to WatchStop!\n\nA multi-functional location services app designed to help you navigate, track, and coordinate with ease.",
            R.drawable.onboarding_watchstoplogopic
        ),
        OnboardingItem(
            "Geofences\n\nCreate custom areas on the map using circles or freehand polygons.\nThese power alarms, groups, and tracking features.",
            R.drawable.onboarding_geofences
        ),
        OnboardingItem(
            "GeoAlarms\n\nSet location-based alarms that trigger when you enter a selected area.\nNever miss your stop again while commuting.",
            R.drawable.onboarding_geoalarms
        ),
        OnboardingItem(
            "Route Tracker\n\nRecord your movement in real time and replay your route.\nView distance, speed, and past positions with precision.",
            R.drawable.onboarding_route
        ),
        OnboardingItem(
            "Group Location Sharing\n\nCreate groups to share live locations.\nCoordinate meetups and track members in real time on a shared map.",
            R.drawable.onboarding_groups
        ),
        OnboardingItem(
            "Smart Scheduling\n\nActivate GeoAlarms by time, date, or day of week.\nFlexible scheduling ensures alarms trigger only when needed.",
            R.drawable.onboarding_schedule
        ),
        OnboardingItem(
            "Cloud Sync & Profiles\n\nSave geofences, routes, and preferences securely.\nAccess your data anytime by logging in.",
            R.drawable.onboarding_firebasepic
        ),
        OnboardingItem(
            "Let's begin!\n\nSet up your first GeoAlarm or create a group."
        )
    )

    val shapes = remember {
        List(15) {
            ShapeState(
                x = Random.nextFloat() * 1080f,
                y = Random.nextFloat() * 1920f,
                size = Random.nextFloat() * 25f + 15f,
                rotation = Random.nextFloat() * 360f,
                alpha = Random.nextFloat() * 0.5f + 0.3f,
                color = Color(
                    Random.nextFloat(),
                    Random.nextFloat(),
                    Random.nextFloat(), 1f
                )
            )
        }
    }

    //leaving link stuff
    val listState = rememberLazyListState()
    val lastIndex = items.lastIndex
    val linkAlpha = remember { Animatable(0f) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .collect { visibleIndices ->
                if (lastIndex in visibleIndices) {
                    delay(1000) //aft last line appear
                    linkAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(1500)
                    )
                }
            }
    }

    //animate: shape
    shapes.forEach { shape ->
        LaunchedEffect(shape) {
            while (true) {
                shape.rotationAnim.animateTo(
                    targetValue = shape.rotationAnim.value + 360f,
                    animationSpec = tween(4000, easing = LinearEasing)
                )
                shape.alphaAnim.animateTo(
                    targetValue = 0.3f + Random.nextFloat() * 0.5f,
                    animationSpec = tween(4000, easing = LinearEasing)
                )
                shape.sizeAnim.animateTo(
                    targetValue = 15f + Random.nextFloat() * 25f,
                    animationSpec = tween(4000, easing = LinearEasing)
                )
            }
        }
    }

    //animate: bg
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            shapes.forEach { shape ->
                val firstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                val screenCenter = size.height / 2
                var velocity = 1f
                if (firstVisible != null) {
                    val itemCenter = firstVisible.offset + firstVisible.size / 2
                    val distanceFromCenter = abs(itemCenter - screenCenter)
                    velocity += distanceFromCenter / 100f
                }
                shape.x += 0.5f * velocity
                shape.y += 0.3f * velocity
                if (shape.x > size.width) shape.x = 0f
                if (shape.y > size.height) shape.y = 0f

                rotate(
                    shape.rotationAnim.value,
                    pivot = Offset(shape.x, shape.y)
                ) {
                    drawCircle(
                        color = shape.color.copy(alpha = shape.alphaAnim.value),
                        radius = shape.sizeAnim.value,
                        center = Offset(shape.x, shape.y)
                    )
                }
            }
        }

        //scrolling text
        Column (
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                    modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
            ) {
                itemsIndexed(items) { index, item ->
                    val itemInfo = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == index }
                    val screenHeight = listState.layoutInfo.viewportEndOffset
                    val centerOffset = screenHeight / 2
                    val distanceFromCenter =
                        if (itemInfo != null) abs(itemInfo.offset + itemInfo.size / 2 - centerOffset)
                        else 0

                    //disappear with scroll
                    val alpha = (1f - (distanceFromCenter.toFloat() / centerOffset.toFloat()))
                        .coerceIn(0f, 1f)

                    //bobbing arrow thingy
                    val infiniteTransition = rememberInfiniteTransition()
                    val bobOffset by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 10f, // move 10.dp up and down
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )

                    Box(
                        modifier = Modifier
                            .fillParentMaxHeight()
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .shadow(6.dp, RoundedCornerShape(16.dp))
                                .background(
                                    if (darkmode)
                                        Color(0xFF2C2C2C).copy(alpha = 0.9f)
                                    else
                                        Color.White.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 20.dp, vertical = 20.dp)
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .align(Alignment.Center)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                item.imageRes?.let {
                                    Image(
                                        painter = painterResource(id = it),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth(0.8f)
                                            .heightIn(min = 180.dp, max = 320.dp)
                                            .padding(bottom = 12.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = item.text,
                                    textAlign = TextAlign.Center,
                                    color = if (darkmode) Color.White.copy(alpha = alpha)
                                        else Color.Black.copy(alpha = alpha),
                                    style = TextStyle(
                                        fontSize = 20.sp,
                                        letterSpacing = 0.3.sp
                                    )
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Scroll Down",
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 32.dp)
                                .offset(y = bobOffset.dp),
                            tint = Color.Black.copy(alpha = alpha)
                        )
                    }
                }

                //return link
                items(1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Continue",
                            style = TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (darkmode) Color.White else Color.Black //TODO ADJUST THEME
                            ),
                            modifier = Modifier
                                .alpha(linkAlpha.value)
                                .clickable { onFinish() },
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

data class OnboardingItem(
    val text: String,
    val imageRes: Int? = null
)