package com.skippy.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 && !Notif.canPost(this)) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { SkippyTheme { App(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.reload()
    }
}

