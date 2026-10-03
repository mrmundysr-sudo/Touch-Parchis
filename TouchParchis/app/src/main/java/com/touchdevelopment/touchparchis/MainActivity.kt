package com.touchdevelopment.touchparchis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.touchdevelopment.touchparchis.data.BoardCoordsLoader
import com.touchdevelopment.touchparchis.data.GameDataLoader
import com.touchdevelopment.touchparchis.engine.KotlinRandomSource
import com.touchdevelopment.touchparchis.ui.GameViewModel
import com.touchdevelopment.touchparchis.ui.TouchParchisApp

/** Single Activity. Loads the JSON data once and hands it to the Compose UI. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val rules = assets.open("game_data.json").use {
            GameDataLoader.parse(it.readBytes().decodeToString())
        }
        val coords = assets.open("board_coords.json").use {
            BoardCoordsLoader.parse(it.readBytes().decodeToString())
        }
        val viewModel = GameViewModel(rules, coords, KotlinRandomSource())

        // Immersive: draw edge to edge with the system bars hidden.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            TouchParchisApp(viewModel, onExit = { finish() })
        }
    }
}
