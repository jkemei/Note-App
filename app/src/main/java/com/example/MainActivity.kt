package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.data.local.AppDatabase
import com.example.ui.MainScreen
import com.example.ui.NoteViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private val viewModel by lazy { NoteViewModel(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && uri.scheme == "drivenotes" && uri.host == "oauth2callback") {
            val code = uri.getQueryParameter("code")
            if (code != null) {
                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val config = db.syncDao().getSyncConfig()
                    val clientId = config?.clientId
                    val clientSecret = config?.clientSecret
                    if (!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()) {
                        viewModel.handleOAuthCallback(code, clientId, clientSecret)
                    } else {
                        Toast.makeText(
                            applicationContext,
                            "Linking Failed: Credentials matching this callback became unconfigured.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
}
