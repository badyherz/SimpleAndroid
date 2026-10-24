package com.simpleandroid.ericardscollector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.simpleandroid.ericardscollector.ui.AppRoot
import com.simpleandroid.ericardscollector.ui.AppViewModel
import com.simpleandroid.ericardscollector.ui.theme.ERICardsCollectorTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ERICardsCollectorTheme {
                AppRoot(viewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app is the moment to save a copy of the collection, if a backup folder is set up
        viewModel.backupIfNeeded()
    }
}
