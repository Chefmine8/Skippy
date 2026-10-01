package com.skippy.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 && !Notif.canPost(this)) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { SkippyTheme { App(vm, onSignIn = ::signIn) } }
    }

    override fun onResume() {
        super.onResume()
        vm.reload()
    }

    /** Interactive Microsoft sign-in (MSAL). Later token renewals are silent. */
    private fun signIn() {
        lifecycleScope.launch {
            try {
                val app = withContext(Dispatchers.IO) {
                    Auth.app(applicationContext).also { a ->
                        // Single-account mode refuses a second sign-in: start clean.
                        if (a.currentAccount?.currentAccount != null) a.signOut()
                    }
                }
                app.signIn(
                    SignInParameters.builder()
                        .withActivity(this@MainActivity)
                        .withScopes(Auth.SCOPES)
                        .withCallback(object : AuthenticationCallback {
                            override fun onSuccess(authenticationResult: IAuthenticationResult) {
                                vm.onMsalSignedIn()
                            }

                            override fun onError(exception: MsalException) {
                                vm.showMessage(getString(R.string.auth_failed, exception.message ?: "?"))
                            }

                            override fun onCancel() {}
                        })
                        .build()
                )
            } catch (e: Exception) {
                vm.showMessage(getString(R.string.auth_failed, e.message ?: "?"))
            }
        }
    }
}
