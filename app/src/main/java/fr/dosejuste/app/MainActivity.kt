package fr.dosejuste.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.dosejuste.app.data.BaseChiffree
import fr.dosejuste.app.data.PrisesRepository
import fr.dosejuste.app.prefs.ReglagesPrefs
import fr.dosejuste.app.ui.DoseJusteApp
import fr.dosejuste.app.ui.JournalRoom
import fr.dosejuste.app.ui.OuvertureViewModel
import java.time.Clock

class MainActivity : ComponentActivity() {

    // L'ouverture de la base se fait dans le ViewModel, sur Dispatchers.IO (jamais sur le fil principal).
    private val ouverture: OuvertureViewModel by viewModels {
        val contexte = applicationContext
        viewModelFactory {
            initializer {
                OuvertureViewModel(ouvrir = {
                    val base = BaseChiffree.ouvrir(contexte)
                    JournalRoom(PrisesRepository(base, Clock.systemUTC()), base)
                })
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val reglages = ReglagesPrefs(applicationContext)
        setContent {
            DoseJusteTheme {
                DoseJusteApp(ouverture = ouverture, reglages = reglages, onFermer = ::finish)
            }
        }
    }
}

@Composable
fun DoseJusteTheme(content: @Composable () -> Unit) {
    fr.dosejuste.app.ui.theme.DoseJusteTheme(content = content)
}
