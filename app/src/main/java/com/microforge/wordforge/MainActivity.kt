package com.microforge.wordforge

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.microforge.wordforge.data.Library
import com.microforge.wordforge.data.Repo
import com.microforge.wordforge.ui.Env
import com.microforge.wordforge.ui.HomeScreen
import com.microforge.wordforge.ui.Leaf
import com.microforge.wordforge.ui.LeafDark
import com.microforge.wordforge.ui.Nav
import com.microforge.wordforge.ui.Registry
import com.microforge.wordforge.ui.Speaker
import com.microforge.wordforge.ui.WordForgeTheme
import kotlinx.coroutines.launch

class AppVm(app: Application) : AndroidViewModel(app) {
    val env: Env

    init {
        Library.load(app)
        val repo = Repo(app)
        val speaker = Speaker(app)
        val rates = floatArrayOf(0.7f, 1.0f, 1.25f)
        speaker.setRate(rates[repo.getInt(Repo.TTS_RATE, 1).coerceIn(0, 2)])
        env = Env(repo, Nav(HomeScreen), speaker)
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
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(listOf(Leaf, LeafDark)))
                            .padding(horizontal = 20.dp, vertical = 24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.mascot),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    "Sanshe Turtle",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                )
                                Text(
                                    "خطوة خطوة، زي السلحفاة 🐢",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    for (entry in Registry.drawer) {
                        NavigationDrawerItem(
                            label = { Text("${entry.emoji}  ${entry.title}", fontWeight = FontWeight.Medium) },
                            selected = nav.current.key == entry.make().key,
                            onClick = {
                                nav.root(entry.make())
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
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
                    title = { Text(nav.current.title, fontWeight = FontWeight.ExtraBold) },
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
                // Slides between screens (direction follows RTL: going deeper enters from the left),
                // and every screen / argument set keeps its own fresh state.
                AnimatedContent(
                    targetState = nav.current,
                    transitionSpec = {
                        val dir = nav.direction
                        if (dir == 0) {
                            fadeIn(tween(260)) togetherWith fadeOut(tween(160))
                        } else {
                            val sign = if (dir > 0) -1 else 1
                            (slideInHorizontally(tween(320)) { sign * it / 3 } + fadeIn(tween(320))) togetherWith
                                (slideOutHorizontally(tween(320)) { -sign * it / 3 } + fadeOut(tween(200)))
                        }
                    },
                    contentKey = { it.key },
                    label = "screens",
                ) { screen ->
                    screen.Content(env)
                }
            }
        }
    }
}
