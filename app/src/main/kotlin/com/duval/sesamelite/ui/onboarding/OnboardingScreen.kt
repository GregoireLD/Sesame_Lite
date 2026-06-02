package com.duval.sesamelite.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duval.sesamelite.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    var skipPermissions by remember { mutableStateOf(false) }
    var prevPage by remember { mutableStateOf(0) }

    // Permission launchers
    val bgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled gracefully */ }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        // Request background location separately after fine location is granted
        if (fineGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    // Trigger permissions when the user advances past the permissions page (swipe or button),
    // unless they explicitly tapped Skip.
    LaunchedEffect(pagerState.currentPage) {
        val current = pagerState.currentPage
        if (current == 3 && prevPage == 2 && !skipPermissions) {
            val perms = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            permissionsLauncher.launch(perms.toTypedArray())
        }
        skipPermissions = false
        prevPage = current
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> HowItWorksPage()
                    2 -> PermissionsPage(
                        onGrant = {
                            scope.launch { pagerState.animateScrollToPage(3) }
                        },
                        onSkip = {
                            skipPermissions = true
                            scope.launch { pagerState.animateScrollToPage(3) }
                        }
                    )
                    3 -> SecurityPage()
                    4 -> ReadyPage(onComplete = onComplete)
                }
            }

            // Page indicator dots
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(5) { index ->
                    val isCurrent = pagerState.currentPage == index
                    val dotColor = if (isCurrent)
                        MaterialTheme.colorScheme.onBackground
                    else
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    Box(
                        modifier = Modifier
                            .size(if (isCurrent) 20.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(dotColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    OnboardingImagePage(
        imageRes = R.drawable.onboarding_welcome,
        title = stringResource(R.string.onboarding_welcome_title),
        subtitle = stringResource(R.string.onboarding_welcome_subtitle)
    )
}

@Composable
private fun HowItWorksPage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.onboarding_howitworks),
            contentDescription = null,
            modifier = Modifier
                .size(280.dp)
                .padding(bottom = 24.dp)
        )
        Text(
            stringResource(R.string.onboarding_howitworks_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(Modifier.height(24.dp))
        Column(modifier = Modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            FeatureRow(icon = Icons.Default.LocationOn, color = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.onboarding_howitworks_feature1_title),
                subtitle = stringResource(R.string.onboarding_howitworks_feature1_subtitle))
            FeatureRow(icon = Icons.Default.Notifications, color = Color(0xFFFF9500),
                title = stringResource(R.string.onboarding_howitworks_feature2_title),
                subtitle = stringResource(R.string.onboarding_howitworks_feature2_subtitle))
            FeatureRow(icon = Icons.Default.Lock, color = Color(0xFF34C759),
                title = stringResource(R.string.onboarding_howitworks_feature3_title),
                subtitle = stringResource(R.string.onboarding_howitworks_feature3_subtitle))
        }
    }
}

@Composable
private fun PermissionsPage(onGrant: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(16.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.onboarding_permissions),
                contentDescription = null,
                modifier = Modifier.size(240.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.onboarding_permissions_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.onboarding_permissions_subtitle), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.onboarding_permissions_note), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        Column(modifier = Modifier.padding(bottom = 80.dp)) {
            Button(onClick = onGrant, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.onboarding_permissions_button), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_permissions_skip), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun SecurityPage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.onboarding_security),
            contentDescription = null,
            modifier = Modifier.size(240.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.onboarding_security_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.onboarding_security_subtitle), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(Modifier.height(24.dp))
        Column(modifier = Modifier.padding(horizontal = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                "1" to R.string.onboarding_security_step1,
                "2" to R.string.onboarding_security_step2,
                "3" to R.string.onboarding_security_step3
            ).forEach { (num, resId) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(24.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(num, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(stringResource(resId), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ReadyPage(onComplete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(16.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.onboarding_ready),
                contentDescription = null,
                modifier = Modifier.size(240.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.onboarding_ready_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.onboarding_ready_subtitle), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().padding(bottom = 80.dp).height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.onboarding_ready_button), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OnboardingImagePage(imageRes: Int, title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            modifier = Modifier.size(280.dp)
        )
        Spacer(Modifier.height(32.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, color: Color, title: String, subtitle: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(28.dp).padding(top = 2.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// (background imported from androidx.compose.foundation)
