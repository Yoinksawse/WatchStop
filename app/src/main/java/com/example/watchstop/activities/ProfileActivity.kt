package com.example.watchstop.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.watchstop.R
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.view.ui.theme.CarbonGrey
import com.example.watchstop.view.ui.theme.Purple40
import com.example.watchstop.view.ui.theme.WatchStopTheme
import com.google.firebase.auth.FirebaseAuth

class ProfileActivity : ComponentActivity() {
    @SuppressLint("UnsafeIntentLaunch")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WatchStopTheme (darkTheme = darkmode) {
                val context = LocalContext.current
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = "${UserProfileObject.userName}'s Profile",
                                fontSize = MaterialTheme.typography.titleLarge.fontSize * X.value) },
                            navigationIcon = {
                                IconButton( onClick = { finish() } ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = if (darkmode) CarbonGrey else Purple40,
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White
                            ),
                        )
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        if (UserProfileObject.isLoggedIn) {
                            ProfileScreen()
                        }
                        else {
                            LaunchedEffect(Unit) {
                                val intent = Intent(context, LoginActivity::class.java)
                                context.startActivity(intent)
                                finish()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen() {
    val accountHandle = UserProfileObject.userName
    val email = UserProfileObject.email
    val context = LocalContext.current
    var isPressed by remember { mutableStateOf(false) }

    // State for the Change Password Dialog
    var showPasswordDialog by remember { mutableStateOf(false) }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { newPassword ->
                val user = FirebaseAuth.getInstance().currentUser
                user?.updatePassword(newPassword)
                    ?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // Important: Update SharedPreferences so auto-login doesn't fail later
                            val userPrefs = context.getSharedPreferences("WatchStopUserPrefs", Activity.MODE_PRIVATE)
                            userPrefs.edit().putString("savedPassword", newPassword).apply()

                            Toast.makeText(context, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                            showPasswordDialog = false
                        } else {
                            Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Account info
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .padding(bottom = 12.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    )
                },
        ) {
            Image(
                painter = painterResource(R.drawable.defaultpfp),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            )

            if (isPressed) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = accountHandle,
            style = MaterialTheme.typography.titleLarge,
            fontSize = MaterialTheme.typography.titleLarge.fontSize * X.value
        )

        Text(
            text = email,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Dark mode switch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Dark Mode", style = MaterialTheme.typography.titleMedium,
                fontSize = MaterialTheme.typography.titleMedium.fontSize * X.value)
            Switch(
                checked = darkmode,
                onCheckedChange = {
                    darkmode = it
                    UserProfileObject.pushToFirebase()
                }
            )
        }

        OutlinedButton(
            onClick = { showPasswordDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Change Password",
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * X.value
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (UserProfileObject.isLoggedIn) {
            Button(
                onClick = {
                    UserProfileObject.signOut()

                    // Clear saved credentials from SharedPreferences
                    val userPrefs = context.getSharedPreferences("WatchStopUserPrefs", Activity.MODE_PRIVATE)
                    userPrefs.edit()
                        .remove("currentLoggedInAccount")
                        .remove("savedPassword")
                        .apply()

                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Logout",
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * X.value
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun ChangePasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Password",
            fontSize = MaterialTheme.typography.titleLarge.fontSize * X.value
        ) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password",
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm New Password",
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (newPassword.isNotEmpty() && confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
                    Text(
                        text = "Passwords do not match",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize * X.value
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = newPassword.isNotEmpty() && newPassword == confirmPassword && newPassword.length >= 6,
                onClick = { onConfirm(newPassword) }
            ) {
                Text("Confirm",
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel",
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value)
            }
        }
    )
}