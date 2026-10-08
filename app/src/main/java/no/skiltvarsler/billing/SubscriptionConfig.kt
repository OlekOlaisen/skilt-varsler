package no.skiltvarsler.billing

/**
 * RevenueCat identifiers configured in the dashboard.
 *
 * Offering packages should use package identifiers [PACKAGE_MONTHLY] and [PACKAGE_YEARLY]
 * (or map product ids `monthly` / `yearly` into those packages). Attach a Paywall to the
 * current offering so [no.skiltvarsler.ui.PaywallScreen] can render it remotely.
 */
object SubscriptionConfig {
    const val ENTITLEMENT_ACCESS = "skilt_varsler_access"
    const val PACKAGE_MONTHLY = "monthly"
    const val PACKAGE_YEARLY = "yearly"
}
