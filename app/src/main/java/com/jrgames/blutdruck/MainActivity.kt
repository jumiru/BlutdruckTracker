package com.jrgames.blutdruck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.jrgames.blutdruck.ui.viewmodel.BlutdruckViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: BlutdruckViewModel by viewModels {
        BlutdruckViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BlutdruckApp(viewModel = viewModel)
        }
    }
}

