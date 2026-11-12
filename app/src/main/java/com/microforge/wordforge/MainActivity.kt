package com.microforge.wordforge

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.microforge.wordforge.data.Library
import com.microforge.wordforge.data.Repo
import com.microforge.wordforge.ui.Env
import com.microforge.wordforge.ui.HomeScreen
import com.microforge.wordforge.ui.Nav
import com.microforge.wordforge.ui.Registry
import com.microforge.wordforge.ui.Speaker
import com.microforge.wordforge.ui.WordForgeTheme
import kotlinx.coroutines.launch

class AppVm(app: Application) : AndroidViewModel(app) {
    val env: Env

    init {
        Library.load(app)
        env = Env(Repo(app), Nav(HomeScreen), Speaker(app))
    }

    override fun onCleared() {
        env.speaker.shutdown()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WordForgeTheme {
                AppRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppVm = viewModel()) {
    val env = vm.env
    val nav = env.nav
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = drawerState.isOpen || nav.canGoBack) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            nav.back()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Word Forge",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    for (entry in Registry.drawer) {
                        NavigationDrawerItem(
                            label = { Text("${entry.emoji}  ${entry.title}") },
                            selected = nav.current.key == entry.make().key,
                            onClick = {
                                nav.root(entry.make())
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(nav.current.title) },
                    navigationIcon = {
                        if (nav.canGoBack) {
                            IconButton(onClick = { nav.back() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "القائمة")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                // key() gives every screen (and every argument set) its own fresh state
                key(nav.current.key) {
                    nav.current.Content(env)
                }
            }
        }
    }
}
