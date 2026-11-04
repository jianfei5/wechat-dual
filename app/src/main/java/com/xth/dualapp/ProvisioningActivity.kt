package com.xth.dualapp

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle

/** 系统建工作资料流程中的回调页，无界面。 */
class ProvisioningActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    when (intent.action) {
      // Android 12+：系统问我们要建哪种资料
      DevicePolicyManager.ACTION_GET_PROVISIONING_MODE -> setResult(
        RESULT_OK,
        Intent().putExtra(
          DevicePolicyManager.EXTRA_PROVISIONING_MODE,
          DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE,
        ),
      )
      // Android 12+：资料建好后在资料内回调
      DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE -> {
        ProfileSetup.apply(this)
        setResult(RESULT_OK)
      }
      // Android 8~11：资料建好后在资料内回调
      DevicePolicyManager.ACTION_PROVISIONING_SUCCESSFUL -> ProfileSetup.apply(this)
    }
    finish()
  }
}
