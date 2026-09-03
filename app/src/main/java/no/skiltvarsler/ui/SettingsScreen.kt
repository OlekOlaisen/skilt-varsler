package no.skiltvarsler.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    tileUrlDraft: String,
    onTileUrlChange: (String) -> Unit,
    onSaveTileUrl: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Innstillinger", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Fliser lastes fra GitHub-releasen. Endre URL bare hvis du peker mot et annet uttrekk.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        OutlinedTextField(
            value = tileUrlDraft,
            onValueChange = onTileUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Flis-URL") },
            singleLine = true,
        )
        OutlinedButton(
            onClick = onSaveTileUrl,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Lagre og hent fliser på nytt")
        }
        Text("Android Auto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Appen er sideloadet. I Android Auto-innstillinger på telefonen: slå på utviklerinnstillinger, tillat ukjente kilder, koble til bilen, og pin Skilt-varsler under Tilpass startside.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text("Personvern", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Posisjon brukes bare på telefonen til å treffe vegnettet. Appen har ikke konto eller analyse. Telefonen treffer aldri NVDB.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}
