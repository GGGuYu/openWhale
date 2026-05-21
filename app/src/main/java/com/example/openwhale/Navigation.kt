package com.example.openwhale

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.openwhale.ui.main.MainScreen

@Composable
fun MainNavigation() {
  MainScreen(modifier = Modifier.safeDrawingPadding(), contentPadding = 16.dp)
}
