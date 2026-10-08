package no.skiltvarsler.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.revenuecat.purchases.ui.revenuecatui.customercenter.CustomerCenter
import kotlinx.coroutines.launch
import no.skiltvarsler.billing.SubscriptionRepository

/**
 * RevenueCat Customer Center for restore, cancel, and support flows.
 * Configure the screens in the RevenueCat dashboard (Pro plan).
 */
@Composable
fun CustomerCenterScreen(
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    CustomerCenter(
        modifier = Modifier.fillMaxSize(),
        onDismiss = {
            scope.launch {
                SubscriptionRepository.refresh(forceNetwork = true)
            }
            onDismiss()
        },
    )
}
