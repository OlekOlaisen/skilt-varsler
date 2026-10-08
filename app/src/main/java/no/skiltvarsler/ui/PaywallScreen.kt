package no.skiltvarsler.ui

import androidx.compose.runtime.Composable
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.ExperimentalPreviewRevenueCatUIPurchasesAPI
import com.revenuecat.purchases.ui.revenuecatui.Paywall
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener
import com.revenuecat.purchases.ui.revenuecatui.PaywallOptions
import no.skiltvarsler.billing.SubscriptionRepository

/**
 * Full-screen RevenueCat Paywall for the current offering.
 * Design the layout in the RevenueCat dashboard and attach it to the offering.
 */
@OptIn(ExperimentalPreviewRevenueCatUIPurchasesAPI::class)
@Composable
fun PaywallScreen(
    onDismiss: () -> Unit,
    onAccessGranted: () -> Unit = {},
) {
    val options = PaywallOptions.Builder(dismissRequest = onDismiss)
        .setShouldDisplayDismissButton(true)
        .setListener(
            object : PaywallListener {
                override fun onPurchaseCompleted(
                    customerInfo: CustomerInfo,
                    storeTransaction: StoreTransaction,
                ) {
                    SubscriptionRepository.applyCustomerInfo(customerInfo)
                    if (SubscriptionRepository.hasEntitlement(customerInfo)) {
                        onAccessGranted()
                    }
                }

                override fun onRestoreCompleted(customerInfo: CustomerInfo) {
                    SubscriptionRepository.applyCustomerInfo(customerInfo)
                    if (SubscriptionRepository.hasEntitlement(customerInfo)) {
                        onAccessGranted()
                    }
                }
            },
        )
        .build()

    Paywall(options = options)
}
