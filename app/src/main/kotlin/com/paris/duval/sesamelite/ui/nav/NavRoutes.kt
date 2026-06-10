package com.paris.duval.sesamelite.ui.nav

object NavRoutes {
    const val LIST = "list"
    const val ADD = "add"
    const val EDIT = "edit/{entryId}"
    const val DETAIL = "detail/{entryId}"
    const val ONBOARDING = "onboarding"
    const val ABOUT = "about"
    const val IMPORT = "import"

    fun editRoute(entryId: String) = "edit/$entryId"
    fun detailRoute(entryId: String) = "detail/$entryId"
}
