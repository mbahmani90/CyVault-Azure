package com.cypressit.cyvault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cypressit.cyvault.feature.authentication.data.remote.ActivityProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ActivityProvider.activity = this
        setContent {
            App()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ActivityProvider.activity = null
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
