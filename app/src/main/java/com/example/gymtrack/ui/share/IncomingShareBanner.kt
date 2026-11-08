package com.example.gymtrack.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtrack.backup.ShareOffer

@Composable
fun IncomingShareBanner(
    offer: ShareOffer,
    canAddMacrocycle: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (offer) {
        is ShareOffer.Macrocycle -> "¿Añadir macrociclo?"
        is ShareOffer.Account -> "¿Añadir cuenta?"
    }
    val body = when (offer) {
        is ShareOffer.Macrocycle ->
            if (canAddMacrocycle) "Se añadirá «${offer.name}» a esta cuenta, inactivo."
            else "Entra en una cuenta para añadir «${offer.name}»."
        is ShareOffer.Account -> "Se creará el perfil «${offer.userName}» sin borrar las cuentas actuales."
    }
    val acceptEnabled = when (offer) {
        is ShareOffer.Macrocycle -> canAddMacrocycle
        is ShareOffer.Account -> true
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onDismiss) { Text("Ahora no") }
                Button(onClick = onAccept, enabled = acceptEnabled) { Text("Añadir") }
            }
        }
    }
}
