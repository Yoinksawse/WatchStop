package com.example.watchstop.activities

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchstop.data.GUEST_USERNAME
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.view.ui.theme.WatchStopTheme
import kotlinx.coroutines.launch


class LoginActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val focusManager = LocalFocusManager.current
            val activity = this

            WatchStopTheme (darkTheme = UserProfileObject.darkmode) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text("Login to WatchStop")
                            },
                            navigationIcon = {
                                IconButton( onClick = { finish() } ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = if (UserProfileObject.darkmode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                titleContentColor = if (UserProfileObject.darkmode) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary,
                                navigationIconContentColor = if (UserProfileObject.darkmode) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                ){innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                focusManager.clearFocus()
                            }
                    ){
                        LoginScreen(activity)
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(activity: Activity) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false)}
    var errorMessage by remember {mutableStateOf<String?>(null)}
    var isLoginMode by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = if (isLoginMode) "Welcome Back" else "Create Account",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = if (UserProfileObject.darkmode) Color.White else MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (isLoginMode) "Sign in to continue" else "Sign up to get started",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (!isLoginMode) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                focusedLabelColor = MaterialTheme.colorScheme.primary
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation =
                if (passwordVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible)
                    Icons.Default.Visibility
                else
                    Icons.Default.VisibilityOff

                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = if (passwordVisible) "Hide password" else "Show password")
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                focusedLabelColor = MaterialTheme.colorScheme.primary
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            )
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = Color.Red,
                fontSize = 14.sp
            )
        }

        val scope = rememberCoroutineScope()

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank() || (!isLoginMode && username.isBlank()))
                    errorMessage = "Please fill in all fields"
                else if (username == GUEST_USERNAME)
                    errorMessage = "Please enter a valid username"
                else {
                    scope.launch {
                        try {
                            if (isLoginMode) {
                                UserProfileObject.signIn(email, password)
                                Toast.makeText(context, "Logged in successfully", Toast.LENGTH_LONG).show()
                            } else {
                                UserProfileObject.signUp(email, password, username)
                                Toast.makeText(context, "Signed up successfully", Toast.LENGTH_LONG).show()
                            }
                            val userPrefs = context.getSharedPreferences("WatchStopUserPrefs", Activity.MODE_PRIVATE)
                            userPrefs.edit()
                                .putString("currentLoggedInAccount", email)
                                .putString("savedPassword", password)
                                .apply()

                            activity.finish()
                            return@launch
                        } catch (e: Exception) {
                            errorMessage = e.localizedMessage ?: e.toString()
                            //TODO: friendly error messages
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = if (isLoginMode) "Sign In" else "Sign Up",
                fontSize = 16.sp
            )
        }

        TextButton(onClick = {
            isLoginMode = !isLoginMode
            errorMessage = null
        }) {
            Text(
                text = if (isLoginMode) "Don't have account? Sign Up" else "Already have account? Sign In",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
