package com.deepfocus.app.util

/**
 * Banking apps that refuse to run while DeepFocus's accessibility service
 * is enabled. While one of these is in the foreground we temporarily
 * disable our accessibility service via WRITE_SECURE_SETTINGS, then
 * re-enable it the moment the user leaves the app.
 *
 * UsageStatsManager polling in BlockingService keeps app blocking working
 * (via the BlockedActivity overlay) during the unblock window, so this
 * does NOT open a bypass for distracting apps.
 *
 * To add a bank: install the bank's app, find the package name (Settings →
 * Apps), add it to BANKING_PACKAGES and rebuild. Banks change their checks
 * over time — if a bank no longer needs this, remove it.
 */
object BankingApps {

    val BANKING_PACKAGES = setOf(
        "uk.co.hsbc.hsbcukmobilebanking",          // HSBC UK
    )

    fun isBankingApp(packageName: String): Boolean {
        return packageName in BANKING_PACKAGES
    }
}
