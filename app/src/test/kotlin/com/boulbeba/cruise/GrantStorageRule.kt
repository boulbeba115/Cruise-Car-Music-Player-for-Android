package com.boulbeba.cruise

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.Shadows

/**
 * Grants storage access before the activity starts, so the start-up permission request (CHANGE_REQUESTS.md CR-03)
 * does not cover the screens under test. Use with a lower `order` than the Compose rule.
 */
class GrantStorageRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            )
            base.evaluate()
        }
    }
}
