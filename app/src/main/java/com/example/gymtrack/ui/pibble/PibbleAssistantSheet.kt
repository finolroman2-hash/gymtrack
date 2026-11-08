package com.example.gymtrack.ui.pibble

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gymtrack.R
import com.example.gymtrack.assistant.PIBBLE_USER_INPUT_MAX_CHARS
import com.example.gymtrack.assistant.PibbleDestination
import com.example.gymtrack.assistant.PibbleNetwork

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PibbleAssistantScreen(
    onBack: () -> Unit,
    onNavigate: (PibbleDestination) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pibble_sheet_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.pibble_back_content_description),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        PibbleAssistantContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            onLeave = onBack,
            onNavigate = onNavigate,
        )
    }
}

@Composable
private fun PibbleAssistantContent(
    modifier: Modifier = Modifier,
    onLeave: () -> Unit,
    onNavigate: (PibbleDestination) -> Unit,
) {
    val context = LocalContext.current
    val application = remember { context.applicationContext as android.app.Application }
    val vm: PibbleAssistantViewModel = viewModel(factory = PibbleAssistantViewModelFactory(application))

    val online = remember { mutableStateOf(PibbleNetwork.isOnline(context)) }
    val pibbleState by vm.pibbleState.collectAsState()
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val ready by vm.ready.collectAsState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        online.value = PibbleNetwork.isOnline(context)
        if (!online.value) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        loadError = null
        val r = vm.prepareModel()
        loadError = r.exceptionOrNull()?.message
        isLoading = false
    }

    DisposableEffect(Unit) {
        onDispose { vm.releaseModelSync() }
    }

    val thinking = pibbleState is PibbleAssistantState.Thinking
    val showRespondiendoSprite = thinking

    Column(
        modifier = modifier
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.pibble_sheet_subtitle),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            ),
        ) {
            Text(
                text = stringResource(R.string.pibble_scope_warning),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Image(
                painter = painterResource(
                    if (showRespondiendoSprite) R.drawable.pibble_respondiendo else R.drawable.pibble_espera,
                ),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentScale = ContentScale.Fit,
            )
        }

        when {
            !online.value -> {
                Text(
                    text = stringResource(R.string.pibble_no_internet),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            isLoading -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.pibble_loading_connect))
                }
            }
            loadError != null || !ready -> {
                Text(
                    text = loadError ?: stringResource(R.string.pibble_connect_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> {
                when (val s = pibbleState) {
                    is PibbleAssistantState.Thinking -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                            Text(
                                text = stringResource(R.string.pibble_thinking),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    is PibbleAssistantState.Success -> {
                        Text(
                            text = s.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        s.navAction?.let { action ->
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { onNavigate(action.destination) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.pibble_nav_button))
                            }
                        }
                    }
                    is PibbleAssistantState.Error -> {
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    is PibbleAssistantState.Idle -> { /* sin respuesta aún */ }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { v ->
                            if (v.length <= PIBBLE_USER_INPUT_MAX_CHARS) input = v
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.pibble_question_label)) },
                        supportingText = {
                            Text(
                                text = stringResource(
                                    R.string.pibble_input_chars,
                                    input.length,
                                    PIBBLE_USER_INPUT_MAX_CHARS,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (input.length >= PIBBLE_USER_INPUT_MAX_CHARS) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        enabled = !thinking,
                    )
                    IconButton(
                        onClick = {
                            val q = input.trim()
                            if (q.isEmpty() || thinking) return@IconButton
                            vm.submitQuery(q)
                            input = ""
                        },
                        enabled = !thinking && input.isNotBlank(),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.pibble_send),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onLeave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.pibble_close))
        }
    }
}
