package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.MainViewModel

@Composable
fun FiltersScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    FilterScreen(viewModel = viewModel, modifier = modifier)
}
