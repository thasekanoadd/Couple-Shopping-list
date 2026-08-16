package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.BentoViewModel
import com.example.ui.viewmodel.BentoViewModelFactory

class MainActivity : ComponentActivity() {
  private var bentoViewModel: BentoViewModel? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: BentoViewModel = viewModel(
        factory = BentoViewModelFactory(application)
      )
      bentoViewModel = viewModel
      val isDarkMode by viewModel.isDarkMode.collectAsState()
      val isKeepScreenOn by viewModel.isKeepScreenOn.collectAsState()

      if (isKeepScreenOn) {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      } else {
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }

      MyApplicationTheme(darkTheme = isDarkMode) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          MainAppScreen(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    bentoViewModel?.onAppResume()
  }

  override fun onPause() {
    super.onPause()
    bentoViewModel?.onAppPauseOrStop()
  }
}
