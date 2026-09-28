package com.example.ui.screens.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.model.MediaType
import com.example.data.local.entity.VaultItemEntity
import com.example.data.vault.PinVerificationResult
import com.example.data.vault.VaultSecurityManager
import com.example.data.vault.VaultStorageManager
import com.example.ui.components.EmptyStateView
import com.example.ui.components.formatDuration
import com.example.ui.components.formatFileSize
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VaultScreen(
    isUnlocked: Boolean,
    hasPinConfigured: Boolean,
    vaultItems: List<VaultItemEntity>,
    onUnlockWithPin: (String) -> Boolean,
    onConfigurePin: (String) -> Unit,
    onLockVault: () -> Unit,
    onRestoreItem: (VaultItemEntity) -> Unit,
    onDeleteItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
    biometricEnabled: Boolean = false,
    canUseBiometrics: Boolean = false,
    onRequestBiometricUnlock: (() -> Unit)? = null,
    onVerifyPinDetailed: ((String) -> PinVerificationResult)? = null,
    onChangePin: ((currentPin: String, newPin: String, onResult: (Boolean, String) -> Unit) -> Unit)? = null,
    onOpenVaultItem: ((VaultItemEntity, List<VaultItemEntity>) -> Unit)? = null,
    onRestoreMultipleItems: ((List<VaultItemEntity>) -> Unit)? = null,
    onDeleteMultipleItems: ((List<Long>) -> Unit)? = null,
    onImportToVault: (() -> Unit)? = null
) {
    if (!isUnlocked) {
        VaultAuthScreen(
            hasPin = hasPinConfigured,
            biometricEnabled = biometricEnabled,
            canUseBiometrics = canUseBiometrics,
            onUnlock = onUnlockWithPin,
            onVerifyPinDetailed = onVerifyPinDetailed,
            onRequestBiometricUnlock = onRequestBiometricUnlock,
            onSetupPin = onConfigurePin,
            modifier = modifier
        )
    } else {
        VaultContentScreen(
            vaultItems = vaultItems,
            onLockVault = onLockVault,
            onRestoreItem = onRestoreItem,
            onDeleteItem = onDeleteItem,
            onOpenVaultItem = onOpenVaultItem,
            onRestoreMultipleItems = onRestoreMultipleItems,
            onDeleteMultipleItems = onDeleteMultipleItems,
            onChangePin = onChangePin,
            onImportToVault = onImportToVault,
            modifier = modifier
        )
    }
}

@Composable
private fun VaultAuthScreen(
    hasPin: Boolean,
    biometricEnabled: Boolean,
    canUseBiometrics: Boolean,
    onUnlock: (String) -> Boolean,
    onVerifyPinDetailed: ((String) -> PinVerificationResult)?,
    onRequestBiometricUnlock: (() -> Unit)?,
    onSetupPin: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableIntStateOf(VaultSecurityManager.getRemainingLockoutSeconds()) }

    LaunchedEffect(lockoutSeconds) {
        while (lockoutSeconds > 0) {
            delay(1000L)
            lockoutSeconds = VaultSecurityManager.getRemainingLockoutSeconds()
            if (lockoutSeconds == 0) {
                errorMessage = null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("vault_auth_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(68.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (!hasPin) {
                if (isConfirming) "Confirma tu PIN" else "Crear PIN de la Bóveda"
            } else "Bóveda Privada Cifrada",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (!hasPin) {
                if (isConfirming) "Vuelve a introducir los 4 dígitos para confirmar."
                else "Define un PIN de 4 dígitos. Tus archivos se cifrarán con AES-256-GCM en almacenamiento privado."
            } else "Introduce tu PIN para descifrar y acceder a tus archivos protegidos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // PIN Dots
        val currentInput = if (isConfirming) confirmPin else pin
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("vault_pin_dots")
        ) {
            repeat(4) { index ->
                val isFilled = index < currentInput.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFilled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            1.dp,
                            if (isFilled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            CircleShape
                        )
                )
            }
        }

        if (lockoutSeconds > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("vault_lockout_banner")
            ) {
                Text(
                    text = "Demasiados intentos fallidos. Espera ${lockoutSeconds}s para volver a intentarlo.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } else if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("vault_auth_error")
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Numpad
        Numpad(
            enabled = lockoutSeconds <= 0,
            showBiometricButton = hasPin && biometricEnabled && canUseBiometrics && onRequestBiometricUnlock != null,
            onBiometricClick = { onRequestBiometricUnlock?.invoke() },
            onDigitClick = { digit ->
                if (lockoutSeconds > 0) return@Numpad
                errorMessage = null
                if (!hasPin) {
                    if (!isConfirming) {
                        if (pin.length < 4) {
                            pin += digit
                            if (pin.length == 4) {
                                isConfirming = true
                            }
                        }
                    } else {
                        if (confirmPin.length < 4) {
                            confirmPin += digit
                            if (confirmPin.length == 4) {
                                if (pin == confirmPin) {
                                    onSetupPin(pin)
                                } else {
                                    errorMessage = "Los PINs no coinciden. Inténtalo de nuevo."
                                    confirmPin = ""
                                    isConfirming = false
                                    pin = ""
                                }
                            }
                        }
                    }
                } else {
                    if (pin.length < 4) {
                        pin += digit
                        if (pin.length == 4) {
                            val submittedPin = pin
                            if (onVerifyPinDetailed != null) {
                                val res = onVerifyPinDetailed(submittedPin)
                                if (!res.isSuccess) {
                                    pin = ""
                                    if (res.isLockedOut) {
                                        lockoutSeconds = res.remainingLockoutSeconds
                                        errorMessage = "Demasiados intentos fallidos. Bloqueo temporal activo."
                                    } else {
                                        val remainingAttempts = (VaultSecurityManager.MAX_FAILED_ATTEMPTS - res.failedAttempts).coerceAtLeast(1)
                                        errorMessage = "PIN incorrecto. Intentos restantes: $remainingAttempts"
                                    }
                                }
                            } else {
                                val success = onUnlock(submittedPin)
                                if (!success) {
                                    errorMessage = "PIN incorrecto."
                                    pin = ""
                                }
                            }
                        }
                    }
                }
            },
            onDeleteClick = {
                errorMessage = null
                if (isConfirming) {
                    if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                } else {
                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                }
            }
        )
    }
}

@Composable
private fun Numpad(
    enabled: Boolean,
    showBiometricButton: Boolean,
    onBiometricClick: () -> Unit,
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    val buttons = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(if (showBiometricButton) "bio" else "", "0", "del")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        buttons.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Box(modifier = Modifier.size(66.dp))
                        "bio" -> {
                            Surface(
                                modifier = Modifier
                                    .size(66.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = enabled) { onBiometricClick() }
                                    .testTag("vault_biometric_btn"),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Fingerprint,
                                        contentDescription = "Desbloquear con biometría",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }
                        "del" -> {
                            Surface(
                                modifier = Modifier
                                    .size(66.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = enabled) { onDeleteClick() }
                                    .testTag("vault_backspace_btn"),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Backspace, contentDescription = "Borrar")
                                }
                            }
                        }
                        else -> {
                            Surface(
                                modifier = Modifier
                                    .size(66.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = enabled) { onDigitClick(key) }
                                    .testTag("vault_digit_$key"),
                                color = if (enabled) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun VaultContentScreen(
    vaultItems: List<VaultItemEntity>,
    onLockVault: () -> Unit,
    onRestoreItem: (VaultItemEntity) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onOpenVaultItem: ((VaultItemEntity, List<VaultItemEntity>) -> Unit)?,
    onRestoreMultipleItems: ((List<VaultItemEntity>) -> Unit)?,
    onDeleteMultipleItems: ((List<Long>) -> Unit)?,
    onChangePin: ((currentPin: String, newPin: String, onResult: (Boolean, String) -> Unit) -> Unit)?,
    onImportToVault: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Todos", "Videos", "Música", "Fotos")

    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelectionMode = selectedIds.isNotEmpty()

    var itemToDeletePermanently by remember { mutableStateOf<VaultItemEntity?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var itemForTechInfo by remember { mutableStateOf<VaultItemEntity?>(null) }
    var showChangePinDialog by remember { mutableStateOf(false) }

    val filteredItems = remember(vaultItems, selectedTab, searchQuery) {
        val tabFiltered = when (selectedTab) {
            1 -> vaultItems.filter { it.mediaType == MediaType.VIDEO.name }
            2 -> vaultItems.filter { it.mediaType == MediaType.AUDIO.name }
            3 -> vaultItems.filter { it.mediaType == MediaType.IMAGE.name }
            else -> vaultItems
        }

        if (searchQuery.isBlank()) {
            tabFiltered
        } else {
            val q = searchQuery.trim()
            tabFiltered.filter {
                it.originalName.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    it.album.contains(q, ignoreCase = true)
            }
        }
    }

    val totalVaultBytes = remember(vaultItems) { vaultItems.sumOf { it.sizeBytes } }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("vault_content_screen"),
        floatingActionButton = {
            if (onImportToVault != null && !isSelectionMode) {
                FloatingActionButton(
                    onClick = onImportToVault,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("vault_import_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Importar a la Bóveda")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            if (isSelectionMode) {
                // Multi-selection bar inside Vault
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().testTag("vault_selection_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedIds = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancelar selección")
                            }
                            Text(
                                text = "${selectedIds.size} seleccionados",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { selectedIds = filteredItems.map { it.id }.toSet() },
                                modifier = Modifier.testTag("vault_select_all_btn")
                            ) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Seleccionar todo")
                            }
                            IconButton(
                                onClick = {
                                    val toRestore = vaultItems.filter { it.id in selectedIds }
                                    selectedIds = emptySet()
                                    if (onRestoreMultipleItems != null) {
                                        onRestoreMultipleItems(toRestore)
                                    } else {
                                        toRestore.forEach { onRestoreItem(it) }
                                    }
                                },
                                modifier = Modifier.testTag("vault_restore_selected_btn")
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = "Restaurar seleccionados")
                            }
                            IconButton(
                                onClick = { showBatchDeleteConfirm = true },
                                modifier = Modifier.testTag("vault_delete_selected_btn")
                            ) {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = "Eliminar seleccionados permanentemente",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            } else {
                // Vault Header Banner
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Bóveda desbloqueada (${vaultItems.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${formatFileSize(totalVaultBytes)} • Cifrado AES-256-GCM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { isSearchVisible = !isSearchVisible },
                                    modifier = Modifier.testTag("vault_search_toggle_btn")
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Buscar en bóveda")
                                }
                                if (onChangePin != null) {
                                    IconButton(
                                        onClick = { showChangePinDialog = true },
                                        modifier = Modifier.testTag("vault_change_pin_btn")
                                    ) {
                                        Icon(Icons.Default.Key, contentDescription = "Cambiar PIN")
                                    }
                                }
                                Button(
                                    onClick = onLockVault,
                                    modifier = Modifier.testTag("lock_vault_btn"),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Bloquear")
                                }
                            }
                        }

                        AnimatedVisibility(visible = isSearchVisible) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .testTag("vault_search_input"),
                                placeholder = { Text("Buscar en la bóveda...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Limpiar")
                                        }
                                    }
                                },
                                singleLine = true
                            )
                        }
                    }
                }
            }

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (filteredItems.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Lock,
                    title = if (searchQuery.isNotEmpty()) "Sin resultados en la bóveda" else "La bóveda está vacía",
                    subtitle = "Tus videos, canciones y fotos protegidos se trasladan al almacenamiento interno privado con cifrado AES-256-GCM y dejan de aparecer en la galería pública."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize().testTag("vault_items_list")
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        val isSelected = item.id in selectedIds
                        val playbackUri = remember(item.vaultFileName, item.encryptedFilePath) {
                            VaultStorageManager.getPlaybackUriForVaultItem(context, item)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                        } else {
                                            onOpenVaultItem?.invoke(item, filteredItems)
                                        }
                                    },
                                    onLongClick = {
                                        selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                    }
                                )
                                .testTag("vault_item_${item.id}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail / Media Icon Box
                                Surface(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clickable {
                                            if (isSelectionMode) {
                                                selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                            } else {
                                                onOpenVaultItem?.invoke(item, filteredItems)
                                            }
                                        }
                                        .testTag("vault_open_${item.id}"),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (item.mediaType == MediaType.IMAGE.name || item.mediaType == MediaType.VIDEO.name) {
                                            AsyncImage(
                                                model = playbackUri,
                                                contentDescription = item.originalName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Icon(
                                            imageVector = when {
                                                isSelected -> Icons.Default.CheckCircle
                                                item.mediaType == MediaType.AUDIO.name -> Icons.Default.Audiotrack
                                                item.mediaType == MediaType.IMAGE.name -> Icons.Default.Visibility
                                                else -> Icons.Default.PlayArrow
                                            },
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.originalName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val durationPart = if (item.durationMs > 0L) " • ${formatDuration(item.durationMs)}" else ""
                                    Text(
                                        text = "${formatFileSize(item.sizeBytes)}$durationPart • AES-256-GCM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (!isSelectionMode) {
                                    IconButton(
                                        onClick = { itemForTechInfo = item },
                                        modifier = Modifier.testTag("vault_info_btn_${item.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = "Información técnica",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRestoreItem(item) },
                                        modifier = Modifier.testTag("vault_restore_btn_${item.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.Restore,
                                            contentDescription = "Restaurar",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { itemToDeletePermanently = item },
                                        modifier = Modifier.testTag("vault_delete_btn_${item.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteForever,
                                            contentDescription = "Eliminar permanentemente",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Single Vault Item Permanent Delete Confirmation Dialog
    if (itemToDeletePermanently != null) {
        val target = itemToDeletePermanently!!
        AlertDialog(
            onDismissRequest = { itemToDeletePermanently = null },
            title = { Text("¿Eliminar definitivamente de la Bóveda?") },
            text = {
                Text("El archivo cifrado '${target.originalName}' se destruirá de forma permanente y no podrá recuperarse.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        itemToDeletePermanently = null
                        onDeleteItem(target.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("vault_confirm_delete_btn")
                ) {
                    Text("Eliminar definitivamente")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDeletePermanently = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Batch Permanent Delete Confirmation Dialog
    if (showBatchDeleteConfirm) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("¿Eliminar $count archivo(s) de la Bóveda?") },
            text = {
                Text("Los archivos cifrados seleccionados serán eliminados permanentemente de la bóveda privada. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ids = selectedIds.toList()
                        showBatchDeleteConfirm = false
                        selectedIds = emptySet()
                        if (onDeleteMultipleItems != null) {
                            onDeleteMultipleItems(ids)
                        } else {
                            ids.forEach { onDeleteItem(it) }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("vault_confirm_batch_delete_btn")
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Technical & Security Info Dialog for Vault Item
    if (itemForTechInfo != null) {
        val info = itemForTechInfo!!
        val dateStr = remember(info.addedAt) {
            runCatching {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(info.addedAt))
            }.getOrDefault("")
        }
        AlertDialog(
            onDismissRequest = { itemForTechInfo = null },
            title = { Text("Detalles de Seguridad y Archivo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultDetailRow("Nombre original", info.originalName)
                    VaultDetailRow("Tipo", info.mimeType.ifEmpty { info.mediaType })
                    VaultDetailRow("Tamaño original", formatFileSize(info.sizeBytes))
                    if (info.durationMs > 0L) {
                        VaultDetailRow("Duración", formatDuration(info.durationMs))
                    }
                    if (info.width > 0 && info.height > 0) {
                        VaultDetailRow("Resolución", "${info.width} × ${info.height}")
                    }
                    VaultDetailRow("Cifrado", "AES-256-GCM (OMNIVAULT_V1)")
                    VaultDetailRow("Archivo en bóveda", info.vaultFileName)
                    if (info.originalFolderPath.isNotEmpty()) {
                        VaultDetailRow("Carpeta origen", info.originalFolderPath)
                    }
                    if (dateStr.isNotEmpty()) {
                        VaultDetailRow("Protegido el", dateStr)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { itemForTechInfo = null }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Change PIN Dialog
    if (showChangePinDialog && onChangePin != null) {
        ChangeVaultPinDialog(
            onDismiss = { showChangePinDialog = false },
            onSubmit = { currentPin, newPin, onResult ->
                onChangePin(currentPin, newPin, onResult)
            }
        )
    }
}

@Composable
private fun ChangeVaultPinDialog(
    onDismiss: () -> Unit,
    onSubmit: (currentPin: String, newPin: String, onResult: (Boolean, String) -> Unit) -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar PIN de la Bóveda") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) currentPin = it },
                    label = { Text("PIN actual (4 dígitos)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("change_pin_current_input")
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) newPin = it },
                    label = { Text("Nuevo PIN (4 dígitos)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("change_pin_new_input")
                )
                OutlinedTextField(
                    value = confirmNewPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) confirmNewPin = it },
                    label = { Text("Confirmar nuevo PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("change_pin_confirm_input")
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    errorMsg = null
                    when {
                        currentPin.length != 4 -> errorMsg = "Introduce tu PIN actual de 4 dígitos."
                        newPin.length != 4 -> errorMsg = "El nuevo PIN debe tener 4 dígitos."
                        newPin != confirmNewPin -> errorMsg = "Los nuevos PINs no coinciden."
                        else -> {
                            onSubmit(currentPin, newPin) { success, msg ->
                                if (success) {
                                    onDismiss()
                                } else {
                                    errorMsg = msg
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.testTag("change_pin_submit_btn")
            ) {
                Text("Guardar PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun VaultDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
