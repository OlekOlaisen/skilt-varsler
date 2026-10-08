# RevenueCat setup for Skilt-varsler

## App identifiers

| Item | Value |
| --- | --- |
| Entitlement | `skilt_varsler_access` |
| Monthly package | `monthly` |
| Yearly package | `yearly` |
| API key (debug / Test Store) | `BuildConfig.REVENUECAT_API_KEY` |

## Dashboard checklist

1. Create entitlement **skilt_varsler_access**.
2. Add products with store product ids that match your Play Console / Test Store products.
3. Create an Offering (mark as **Current**) with packages:
   - identifier `monthly` → monthly product
   - identifier `yearly` → yearly product
4. Attach both packages to entitlement `skilt_varsler_access`.
5. Design a **Paywall** for that offering in the RevenueCat dashboard.
6. (Optional, Pro) Configure **Customer Center** for restore / cancel / support.

## API keys

Debug and local sideloads use the Test Store key by default.

For a Play Store release, pass a Google API key:

```bash
./gradlew :app:assembleRelease -PrevenueCatApiKey=goog_YOUR_KEY
```

Never ship a `test_` key in a Play production build — the SDK refuses to run with Test Store keys in release mode unless you intentionally opt out for internal testing.

## App behaviour

- Starting a trip without `skilt_varsler_access` opens the Paywall.
- Auto-start only runs when the entitlement is already active.
- Settings → **Abonner** / **Administrer abonnement** / **Gjenopprett kjøp**.
