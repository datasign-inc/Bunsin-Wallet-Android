package jp.datasign.bunsin_wallet.ui.certificate

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import jp.datasign.bunsin_wallet.R
import androidx.navigation.fragment.findNavController

class CertificateFragmentMenuProvider(
    private val fragment: Fragment,
    private val menuInflater: MenuInflater
) : MenuProvider {

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        // メニューをインフレート
        menuInflater.inflate(R.menu.menu_cancel, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        // メニューアイテムの選択を処理
        return when (menuItem.itemId) {
            R.id.action_cancel -> {
                // キャンセルが選択されたときの処理
                fragment.findNavController().navigateUp()
                true
            }

            else -> false
        }
    }
}