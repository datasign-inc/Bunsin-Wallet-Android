package jp.datasign.bunsin_wallet.utils

import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import jp.datasign.bunsin_wallet.R

object DisplayUtil {
    fun setFragmentTitle(activity: AppCompatActivity?, title: String) {
        activity?.supportActionBar?.customView?.findViewById<TextView>(R.id.action_bar_title)?.text =
            title
    }
}