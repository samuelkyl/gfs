package ca.sleung.gfs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.sleung.gfs.ui.navigation.GFSNavGraph
import ca.sleung.gfs.ui.navigation.NavDestination
import ca.sleung.gfs.ui.theme.GFSTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            GFSTheme {
                LifecycleResumeEffect(Unit) {
                    mainViewModel.onAppResumed()
                    onPauseOrDispose {}
                }

                val hasCompletedOnboarding by mainViewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()
                var lockedInitialOnboardingState by rememberSaveable { mutableStateOf<Boolean?>(null) }

                if (lockedInitialOnboardingState == null && hasCompletedOnboarding != null) {
                    lockedInitialOnboardingState = hasCompletedOnboarding
                }

                when (lockedInitialOnboardingState) {
                    null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        )
                    }
                    true -> {
                        GFSNavGraph(startDestination = NavDestination.Dashboard)
                    }
                    false -> {
                        GFSNavGraph(startDestination = NavDestination.Onboarding)
                    }
                }
            }
        }
    }
}
