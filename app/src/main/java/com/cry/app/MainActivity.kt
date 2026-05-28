package com.cry.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cry.app.ui.PriceScreen

class MainActivity : ComponentActivity() {
    private val vm: PriceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black,
                    surface = Color.Black,
                ),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black,
                ) {
                    val pairs by vm.pairs.collectAsStateWithLifecycle()
                    val tickers by vm.tickers.collectAsStateWithLifecycle()
                    val busy by vm.busy.collectAsStateWithLifecycle()
                    val addError by vm.addError.collectAsStateWithLifecycle()

                    PriceScreen(
                        pairs = pairs,
                        tickers = tickers,
                        busy = busy,
                        addError = addError,
                        onAdd = vm::addPair,
                        onRemove = vm::removePair,
                        onClearError = vm::clearAddError,
                    )
                }
            }
        }
    }
}
