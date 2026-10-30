package com.idp.geostampcamera.ui.settings

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.idp.geostampcamera.R
import com.idp.geostampcamera.data.repository.SettingsRepository
import com.idp.geostampcamera.databinding.FragmentSettingsBinding
import com.idp.geostampcamera.drive.DriveSyncManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    private var pendingDriveEmail: String? = null

    private val driveAccountPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
        val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) ?: return@registerForActivityResult
        probeDriveConsent(email)
    }

    private val driveConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        val email = pendingDriveEmail ?: return@registerForActivityResult
        pendingDriveEmail = null
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            probeDriveConsent(email)
        } else {
            Toast.makeText(requireContext(), R.string.settings_storage_drive_connect_failed, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.settingsToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        val adapter = SettingsAdapter(
            onToggle = { row, value -> viewModel.toggle(row, value) },
            onPick = { row -> showPicker(row) },
            onAction = { row -> handleAction(row) }
        )
        binding.settingsList.layoutManager = LinearLayoutManager(requireContext())
        binding.settingsList.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.rows.collect { adapter.submit(it) }
        }
    }

    private fun showPicker(row: SettingsRow) {
        val choices = row.choices ?: return
        val labels = choices.map { it.second }.toTypedArray()
        val selected = choices.indexOfFirst { it.first == row.currentValue }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(row.title)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                val picked = choices[which].first
                if (row.id == SettingsAction.STORAGE_LOCATION && picked == SettingsRepository.STORAGE_DRIVE) {
                    dialog.dismiss()
                    launchDriveAccountPicker()
                } else {
                    viewModel.setChoice(row.id, picked)
                    dialog.dismiss()
                }
            }
            .show()
    }

    private fun launchDriveAccountPicker() {
        val intent = AccountManager.newChooseAccountIntent(
            null, null, arrayOf("com.google"), null, null, null, null
        )
        try {
            driveAccountPicker.launch(intent)
            Toast.makeText(
                requireContext(),
                R.string.settings_storage_drive_pick_account,
                Toast.LENGTH_LONG
            ).show()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                requireContext(),
                R.string.settings_storage_drive_no_accounts,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Fetches a drive.file OAuth token for [email] in the background to force the
     * consent dialog up-front. On success we persist the email and switch storage
     * to Drive; on UserRecoverableAuthException we launch its resolution intent
     * and resume here when the user grants consent.
     */
    private fun probeDriveConsent(email: String) {
        Toast.makeText(requireContext(), R.string.settings_storage_drive_connecting, Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    GoogleAuthUtil.getToken(requireContext(), Account(email, "com.google"), DriveSyncManager.OAUTH_SCOPE)
                }
            }
            outcome.fold(
                onSuccess = {
                    viewModel.setDriveAccount(email)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.settings_storage_drive_connected_as, email),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = { t ->
                    Log.w("DriveSync", "getToken failed for scope ${DriveSyncManager.OAUTH_SCOPE}", t)
                    when (t) {
                        is UserRecoverableAuthException -> {
                            pendingDriveEmail = email
                            try {
                                driveConsentLauncher.launch(t.intent)
                            } catch (_: ActivityNotFoundException) {
                                pendingDriveEmail = null
                                showDriveError(t)
                            }
                        }
                        else -> showDriveError(t)
                    }
                }
            )
        }
    }

    /**
     * The hard-failure path. We split by exception type so the user sees a message that
     * tells them *why* the connect failed:
     *   • [GoogleAuthException] with an "UNREGISTERED_ON_API_CONSOLE" / "INVALID_SCOPE" /
     *     "BAD_REQUEST" code → the app's package+SHA-1 isn't registered in Google Cloud
     *     Console (or the Drive API isn't enabled on that project). Shown as a long dialog
     *     because the fix is on the Google Cloud side, not in-app.
     *   • [IOException] → network issue, show the short network-failed string.
     *   • Anything else → generic failure + the exception message appended, so a tester
     *     can at least report what Google returned.
     */
    private fun showDriveError(t: Throwable) {
        val message = t.message.orEmpty()
        val looksLikeConfigError = t is GoogleAuthException && CONFIG_ERROR_HINTS.any { message.contains(it, ignoreCase = true) }
        when {
            looksLikeConfigError -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.settings_storage_drive_connect_failed)
                    .setMessage(getString(R.string.settings_storage_drive_setup_needed) + "\n\n(" + message + ")")
                    .setPositiveButton(R.string.common_ok, null)
                    .show()
            }
            t is IOException -> Toast.makeText(requireContext(), R.string.settings_storage_drive_network_failed, Toast.LENGTH_LONG).show()
            else -> {
                val detail = message.ifBlank { t::class.java.simpleName }
                Toast.makeText(
                    requireContext(),
                    getString(R.string.settings_storage_drive_error_detail, detail),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun handleAction(row: SettingsRow) {
        when (row.id) {
            SettingsAction.PRIVACY_POLICY -> showPrivacyPolicy()
            SettingsAction.HELP -> findNavController().navigate(R.id.action_global_help)
            SettingsAction.HOW_TO_USE,
            SettingsAction.FAQ,
            SettingsAction.VIDEO_TUTORIALS -> findNavController().navigate(R.id.action_global_help)
            SettingsAction.LANGUAGE -> findNavController().navigate(R.id.action_global_language)
            SettingsAction.FILENAME_BUILDER -> findNavController().navigate(R.id.action_global_filename)
            SettingsAction.SAVED_LOCATIONS -> findNavController().navigate(R.id.action_global_savedLocations)
            SettingsAction.GO_PREMIUM -> findNavController().navigate(R.id.action_global_paywall)
            SettingsAction.RATE_APP -> openPlayStore()
            SettingsAction.SHARE_APP -> shareApp()
            SettingsAction.MORE_APPS -> openMoreApps()
            SettingsAction.PHOTO_REPORT -> showNotice(R.string.settings_photo_report)
            else -> {}
        }
    }

    private fun showPrivacyPolicy() {
        val url = getString(R.string.privacy_policy_url)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_privacy_policy)
                .setMessage(getString(R.string.privacy_policy_body))
                .setPositiveButton(R.string.common_ok, null)
                .show()
        }
    }

    private fun openPlayStore() {
        val pkg = requireContext().packageName
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun shareApp() {
        val pkg = requireContext().packageName
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check out GPS Map Camera - GeoTag: https://play.google.com/store/apps/details?id=$pkg")
        }
        startActivity(Intent.createChooser(send, getString(R.string.settings_share_app)))
    }

    private fun openMoreApps() {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pub:IDP Tech")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun showNotice(resId: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(resId)
            .setPositiveButton(R.string.common_ok, null)
            .show()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        com.idp.geostampcamera.ui.common.AdBanner.install(this)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private companion object {
        // Strings Google returns when the OAuth client isn't configured for this app's
        // package name + SHA-1 fingerprint, or the scope hasn't been approved. The exact
        // code varies by Play Services version; checking all three catches the common ones.
        val CONFIG_ERROR_HINTS = listOf(
            "UNREGISTERED_ON_API_CONSOLE",
            "INVALID_SCOPE",
            "BAD_REQUEST",
            "NEED_PERMISSION"
        )
    }
}
