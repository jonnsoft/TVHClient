package org.tvheadend.tvhclient.ui.features.unlocker

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import com.afollestad.materialdialogs.MaterialDialog
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import org.tvheadend.tvhclient.MainApplication
import org.tvheadend.tvhclient.R
import org.tvheadend.tvhclient.ui.common.tasks.HtmlFileLoaderTask
import org.tvheadend.tvhclient.ui.features.information.WebViewFragment
import org.tvheadend.tvhclient.ui.features.startup.SplashActivity
import org.tvheadend.tvhclient.util.billing.BillingHandler
import org.tvheadend.tvhclient.util.billing.BillingManager
import org.tvheadend.tvhclient.util.billing.BillingManager.UNLOCKER
import org.tvheadend.tvhclient.util.billing.BillingUpdatesListener
import timber.log.Timber

class UnlockerFragment : WebViewFragment(), HtmlFileLoaderTask.Listener, BillingUpdatesListener {

    private lateinit var billingManager: BillingManager
    private lateinit var billingHandler: BillingHandler

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        toolbarInterface.setTitle(getString(R.string.pref_unlocker))
        toolbarInterface.setSubtitle("")
        billingManager = MainApplication.getInstance().billingManager
        billingHandler = MainApplication.getInstance().billingHandler
    }

    override fun onResume() {
        super.onResume()
        billingHandler.addListener(this)
    }

    override fun onPause() {
        super.onPause()
        billingHandler.removeListener(this)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.unlocker_options_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_purchase -> {
                if (!MainApplication.getInstance().isUnlocked) {
                    Timber.d("Unlocker not purchased")
                    billingManager.initiatePurchaseFlow(activity, UNLOCKER, null, BillingClient.SkuType.INAPP)
                } else {
                    Timber.d("Unlocker already purchased")
                    showPurchasedAlreadyMadeDialog()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showPurchaseNotSuccessfulDialog() {
        Timber.d("Unlocker purchase not successful")
        context?.let {
            MaterialDialog.Builder(it)
                    .title(R.string.dialog_title_purchase_not_successful)
                    .content(R.string.dialog_content_purchase_not_successful)
                    .canceledOnTouchOutside(false)
                    .positiveText(android.R.string.ok)
                    .onPositive { dialog, _ -> dialog.dismiss() }
                    .show()
        }
    }

    private fun showPurchaseSuccessfulDialog() {
        Timber.d("Unlocker purchase successful")
        context?.let {
            MaterialDialog.Builder(it)
                    .title(R.string.dialog_title_purchase_successful)
                    .content(R.string.dialog_content_purchase_successful)
                    .canceledOnTouchOutside(false)
                    .positiveText(R.string.dialog_button_restart)
                    .onPositive { _, _ ->
                        // Restart the app so that the unlocker will be activated
                        val intent = Intent(activity, SplashActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        activity?.startActivity(intent)
                    }
                    .show()
        }
    }

    private fun showPurchasedAlreadyMadeDialog() {
        Timber.d("Unlocker already purchased")
        context?.let {
            MaterialDialog.Builder(it)
                    .title(R.string.dialog_title_purchase_already_made)
                    .content(R.string.dialog_content_purchase_already_made)
                    .canceledOnTouchOutside(false)
                    .positiveText(android.R.string.ok)
                    .onPositive { dialog, _ -> dialog.dismiss() }
                    .show()
        }
    }

    override fun onBillingClientSetupFinished() {

    }

    override fun onConsumeFinished(token: String, result: Int) {

    }

    override fun onPurchaseSuccessful(purchases: List<Purchase>?) {
        Timber.d("Purchase was successful")
        showPurchaseSuccessfulDialog()
    }

    override fun onPurchaseCancelled() {
        Timber.d("Purchase was cancelled")
    }

    override fun onPurchaseError(errorCode: Int) {
        Timber.d("Purchase was not successful")
        showPurchaseNotSuccessfulDialog()
    }
}
