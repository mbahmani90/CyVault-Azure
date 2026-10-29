package com.cypressit.cyvault.testtags

object VaultHomeTags {
    const val ADD_ITEM_BUTTON = "vault_home_add_item_button"
    const val BALANCE_LIST = "vault_home_balance_list"
    const val BALANCES_EMPTY_TEXT = "vault_home_balances_empty_text"

    // Every balance row has the same tag; tests tell rows apart by their text.
    const val BALANCE_ROW = "vault_home_balance_row"
}
