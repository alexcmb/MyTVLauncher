package crazyboyfeng.justTvLauncher.model

/** A non-app card action shown as its own card in the browse grid. */
sealed interface CardAction {
    val title: String
}

/** Triggers the manual update check. */
class UpdateAction(override val title: String) : CardAction

/** Opens the dialog to restore hidden apps. */
class HiddenAppsAction(override val title: String) : CardAction
