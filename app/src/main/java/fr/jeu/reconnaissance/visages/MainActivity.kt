package fr.jeu.reconnaissance.visages

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import fr.jeu.reconnaissance.visages.ui.AppReconnaissance
import fr.jeu.reconnaissance.visages.ui.Theme

class MainActivity : ComponentActivity() {
    private val vm: JeuViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 8-9 : la sauvegarde dans Téléchargements demande l'autorisation de stockage.
        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
        if (savedInstanceState == null) traiter(intent)
        setContent {
            Theme { AppReconnaissance(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        traiter(intent)
    }

    override fun onStop() {
        super.onStop()
        vm.sauvegardeAutomatique()
    }

    private fun traiter(intent: Intent?) {
        if (intent?.getBooleanExtra(Rappels.EXTRA_JOUER, false) == true) {
            intent.removeExtra(Rappels.EXTRA_JOUER)
            vm.ouvertureDepuisRappel()
        }
    }
}
