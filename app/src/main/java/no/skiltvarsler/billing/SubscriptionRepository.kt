package no.skiltvarsler.billing

import android.app.Activity
import android.app.Application
import android.util.Log
import com.revenuecat.purchases.CacheFetchPolicy
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import no.skiltvarsler.BuildConfig

data class SubscriptionState(
    val customerInfo: CustomerInfo? = null,
    val hasAccess: Boolean = false,
    val isLoading: Boolean = true,
    val lastError: String? = null,
)

/**
 * Thin wrapper around [Purchases] for entitlement checks, offerings, and restores.
 * Configure once from [Application.onCreate].
 */
object SubscriptionRepository {
    private const val TAG = "Subscription"

    private val stateInternal = MutableStateFlow(SubscriptionState())
    val state: StateFlow<SubscriptionState> = stateInternal.asStateFlow()

    val hasAccess: Boolean
        get() = stateInternal.value.hasAccess

    fun configure(application: Application) {
        Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO
        Purchases.configure(
            PurchasesConfiguration.Builder(application, BuildConfig.REVENUECAT_API_KEY).build(),
        )
        Purchases.sharedInstance.updatedCustomerInfoListener =
            UpdatedCustomerInfoListener { info -> publish(info) }
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { error ->
                Log.w(TAG, "CustomerInfo error: ${error.message}")
                stateInternal.update { current ->
                    current.copy(isLoading = false, lastError = error.message)
                }
            },
            onSuccess = { info -> publish(info) },
        )
    }

    fun hasEntitlement(info: CustomerInfo, entitlementId: String = SubscriptionConfig.ENTITLEMENT_ACCESS): Boolean {
        return info.entitlements[entitlementId]?.isActive == true
    }

    suspend fun refresh(forceNetwork: Boolean = false): Result<CustomerInfo> {
        return runCatching {
            val policy = if (forceNetwork) {
                CacheFetchPolicy.FETCH_CURRENT
            } else {
                CacheFetchPolicy.default()
            }
            val info = Purchases.sharedInstance.awaitCustomerInfo(fetchPolicy = policy)
            publish(info)
            info
        }.onFailure { error ->
            Log.w(TAG, "refresh failed", error)
            stateInternal.update { current ->
                current.copy(
                    isLoading = false,
                    lastError = purchasesMessage(error),
                )
            }
        }
    }

    suspend fun offerings(): Result<Offerings> {
        return runCatching {
            Purchases.sharedInstance.awaitOfferings()
        }.onFailure { error ->
            Log.w(TAG, "offerings failed", error)
            stateInternal.update { current ->
                current.copy(lastError = purchasesMessage(error))
            }
        }
    }

    suspend fun currentOffering(): Result<Offering?> {
        return offerings().map { offerings -> offerings.current }
    }

    fun packageFor(offering: Offering, packageId: String): Package? {
        return offering.availablePackages.firstOrNull { packageItem ->
            packageItem.identifier.equals(packageId, ignoreCase = true)
        }
    }

    suspend fun purchase(activity: Activity, packageToPurchase: Package): Result<CustomerInfo> {
        return runCatching {
            val result = Purchases.sharedInstance.awaitPurchase(
                PurchaseParams.Builder(activity, packageToPurchase).build(),
            )
            publish(result.customerInfo)
            result.customerInfo
        }.onFailure { error ->
            Log.w(TAG, "purchase failed", error)
            stateInternal.update { current ->
                current.copy(lastError = purchasesMessage(error))
            }
        }
    }

    suspend fun purchaseMonthly(activity: Activity): Result<CustomerInfo> {
        val offering = currentOffering().getOrElse { return Result.failure(it) }
            ?: return Result.failure(IllegalStateException("Ingen tilbud tilgjengelig"))
        val packageItem = packageFor(offering, SubscriptionConfig.PACKAGE_MONTHLY)
            ?: return Result.failure(IllegalStateException("Månedlig abonnement mangler i tilbudet"))
        return purchase(activity, packageItem)
    }

    suspend fun purchaseYearly(activity: Activity): Result<CustomerInfo> {
        val offering = currentOffering().getOrElse { return Result.failure(it) }
            ?: return Result.failure(IllegalStateException("Ingen tilbud tilgjengelig"))
        val packageItem = packageFor(offering, SubscriptionConfig.PACKAGE_YEARLY)
            ?: return Result.failure(IllegalStateException("Årlig abonnement mangler i tilbudet"))
        return purchase(activity, packageItem)
    }

    suspend fun restore(): Result<CustomerInfo> {
        return runCatching {
            val info = Purchases.sharedInstance.awaitRestore()
            publish(info)
            info
        }.onFailure { error ->
            Log.w(TAG, "restore failed", error)
            stateInternal.update { current ->
                current.copy(lastError = purchasesMessage(error))
            }
        }
    }

    fun applyCustomerInfo(info: CustomerInfo) {
        publish(info)
    }

    private fun publish(info: CustomerInfo) {
        stateInternal.value = SubscriptionState(
            customerInfo = info,
            hasAccess = hasEntitlement(info),
            isLoading = false,
            lastError = null,
        )
    }

    private fun purchasesMessage(error: Throwable): String {
        return when (error) {
            is PurchasesException -> error.error.message
            else -> error.message ?: "Ukjent abonnementsfeil"
        }
    }
}
