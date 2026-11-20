package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
  @Serializable data object Dashboard : AppRoute

  @Serializable data object HistoryLearning : AppRoute

  @Serializable data object SafetySettings : AppRoute

  @Serializable data object SetupGuide : AppRoute
}
