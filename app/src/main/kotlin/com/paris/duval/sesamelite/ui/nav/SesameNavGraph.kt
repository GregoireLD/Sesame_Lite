package com.paris.duval.sesamelite.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.paris.duval.sesamelite.share.ImportExport
import com.paris.duval.sesamelite.ui.about.AboutScreen
import com.paris.duval.sesamelite.ui.addedit.AddEditScreen
import com.paris.duval.sesamelite.ui.addedit.AddEditViewModel
import com.paris.duval.sesamelite.ui.detail.DetailScreen
import com.paris.duval.sesamelite.ui.detail.DetailViewModel
import com.paris.duval.sesamelite.ui.list.ListScreen
import com.paris.duval.sesamelite.ui.list.ListViewModel
import com.paris.duval.sesamelite.ui.onboarding.OnboardingScreen
import com.paris.duval.sesamelite.ui.share.QRShareScreen
import com.paris.duval.sesamelite.ui.share.QRShareViewModel

@Composable
fun SesameNavGraph(
    navController: NavHostController,
    startDestination: String = NavRoutes.LIST,
    pendingImportUri: String?,
    onImportConsumed: () -> Unit,
    pendingEntryId: String?,
    onEntryIdConsumed: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onResetAllData: () -> Unit
) {
    val listVm: ListViewModel = viewModel()

    // Handle incoming sesame://import#… URI
    LaunchedEffect(pendingImportUri) {
        if (pendingImportUri != null) {
            val encoded = java.net.URLEncoder.encode(pendingImportUri, "UTF-8")
            navController.navigate("${NavRoutes.IMPORT}?uri=$encoded")
            onImportConsumed()
        }
    }

    // Handle notification tap deep link to a specific entry
    LaunchedEffect(pendingEntryId) {
        if (pendingEntryId != null) {
            navController.navigate(NavRoutes.detailRoute(pendingEntryId))
            onEntryIdConsumed()
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(NavRoutes.LIST) {
            ListScreen(
                vm = listVm,
                onAddEntry = { navController.navigate(NavRoutes.ADD) },
                onOpenEntry = { id -> navController.navigate(NavRoutes.detailRoute(id)) },
                onAbout = { navController.navigate(NavRoutes.ABOUT) }
            )
        }

        composable(NavRoutes.ADD) {
            val vm: AddEditViewModel = viewModel()
            LaunchedEffect(Unit) { vm.initForAdd() }
            AddEditScreen(vm = vm, onDismiss = { navController.popBackStack() })
        }

        composable(
            NavRoutes.EDIT,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("entryId") ?: return@composable
            val vm: AddEditViewModel = viewModel()
            LaunchedEffect(id) { vm.initForEdit(id) }
            AddEditScreen(
                vm = vm,
                onDismiss = { navController.popBackStack() },
                onDeleted = { navController.popBackStack(NavRoutes.LIST, inclusive = false) }
            )
        }

        composable(
            NavRoutes.DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("entryId") ?: return@composable
            DetailScreen(
                vm = viewModel<DetailViewModel>(),
                entryId = id,
                onDismiss = { navController.popBackStack() },
                onEdit = { eid -> navController.navigate(NavRoutes.editRoute(eid)) },
                onShare = { eid -> navController.navigate("qr/$eid") }
            )
        }

        composable(
            "qr/{entryId}",
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("entryId") ?: return@composable
            QRShareScreen(vm = viewModel<QRShareViewModel>(), entryId = id, onDismiss = { navController.popBackStack() })
        }

        composable(NavRoutes.ONBOARDING) {
            OnboardingScreen(onComplete = { navController.popBackStack() })
        }

        composable(NavRoutes.ABOUT) {
            AboutScreen(
                onDismiss = { navController.popBackStack() },
                onReplayOnboarding = {
                    navController.popBackStack()
                    onReplayOnboarding()
                },
                onResetAllData = onResetAllData,
                onResetPermissionBanners = listVm::resetPermissionBanners
            )
        }

        composable(
            "${NavRoutes.IMPORT}?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType; nullable = true })
        ) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString("uri") ?: ""
            val uriString = try { java.net.URLDecoder.decode(encoded, "UTF-8") } catch (_: Exception) { "" }
            when (val result = ImportExport.parse(uriString)) {
                is ImportExport.ImportResult.Success -> {
                    val vm: AddEditViewModel = viewModel()
                    LaunchedEffect(uriString) { vm.initForImport(result.import) }
                    AddEditScreen(vm = vm, onDismiss = { navController.popBackStack() })
                }
                is ImportExport.ImportResult.FutureVersion ->
                    UnknownImportScreen(ImportErrorKind.FUTURE_VERSION) { navController.popBackStack() }
                is ImportExport.ImportResult.EmptyEntry ->
                    UnknownImportScreen(ImportErrorKind.EMPTY) { navController.popBackStack() }
                else ->
                    UnknownImportScreen(ImportErrorKind.MALFORMED) { navController.popBackStack() }
            }
        }
    }
}
