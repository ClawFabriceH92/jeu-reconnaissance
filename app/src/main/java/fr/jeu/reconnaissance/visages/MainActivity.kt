package fr.jeu.reconnaissance.visages

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import fr.jeu.reconnaissance.visages.ui.AppReconnaissance
import fr.jeu.reconnaissance.visages.ui.Theme

class MainActivity : ComponentActivity() {
    private val vm: JeuViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Theme { AppReconnaissance(vm) }
        }
    }
}
