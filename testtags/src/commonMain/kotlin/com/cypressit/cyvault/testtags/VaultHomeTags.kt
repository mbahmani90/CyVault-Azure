package com.cypressit.cyvault.testtags

object VaultHomeTags {
    const val ADD_ITEM_BUTTON = "vault_home_add_item_button"
    const val BALANCE_LIST = "vault_home_balance_list"
    const val BALANCES_EMPTY_TEXT = "vault_home_balances_empty_text"

    // Every balance row has the same tag; tests tell rows apart by their text.
    const val BALANCE_ROW = "vault_home_balance_row"

    // The top bar in selection mode
    const val SELECTION_COUNT_TEXT = "vault_home_selection_count_text"
    const val SELECTION_CLOSE_BUTTON = "vault_home_selection_close_button"
    const val SELECTION_SELECT_ALL_BUTTON = "vault_home_selection_select_all_button"
    const val SELECTION_DELETE_BUTTON = "vault_home_selection_delete_button"
}
