package jp.datasign.bunsin_wallet

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import jp.datasign.bunsin_wallet.databinding.ActivityMainBinding
import jp.datasign.bunsin_wallet.datastore.PreferencesDataStore
import jp.datasign.bunsin_wallet.utils.BiometricUtil
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    companion object {
        private const val LOCK_THRESHOLD_MS = 5 * 60 * 1000L
    }

    private lateinit var binding: ActivityMainBinding

    // enrollBiometricRequestの定義
    private val enrollBiometricRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d("MainActivity", "ResultCode: ${result.resultCode}")
            val dataStore = PreferencesDataStore(this)
            lifecycleScope.launch {
                if (result.resultCode != Activity.RESULT_CANCELED) {
                    // 生体認証の設定が完了した場合の処理
                    Toast.makeText(
                        this@MainActivity,
                        "生体認証の設定が完了しました。",
                        Toast.LENGTH_SHORT
                    ).show()
                    dataStore.saveShouldLock(false)
                } else {
                    // 生体認証の設定がキャンセルまたは失敗した場合の処理
                    Toast.makeText(
                        this@MainActivity,
                        "生体認証の設定がキャンセルまたは失敗しました。",
                        Toast.LENGTH_SHORT
                    ).show()
                    dataStore.saveShouldLock(false)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ctx = this
        supportActionBar?.apply {
            displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
            setCustomView(R.layout.custom_action_bar)
            val color = ContextCompat.getColor(ctx, R.color.backgroundColorPrimary)
            setBackgroundDrawable(ColorDrawable(color))
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intentからデータ（URI）を取得
        val data: Uri? = intent?.data
        Log.d("MainActivity", "data = $data")

        binding.navView.post {
            val navController = findNavController(R.id.nav_host_fragment_activity_main)

            val appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.navigation_certificate,
                    R.id.navigation_recipient,
                    R.id.navigation_reader,
                    R.id.navigation_settings
                )
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            binding.navView.setupWithNavController(navController)
            navController.addOnDestinationChangedListener { _, destination, _ ->
                when (destination.id) {
                    R.id.navigation_certificate -> {
                        supportActionBar?.setDisplayHomeAsUpEnabled(false)
                    }

                    R.id.credentialDetailFragment, R.id.issuerDetailFragment -> {
                        supportActionBar?.setDisplayHomeAsUpEnabled(true)
                    }
                }
            }
            // URIからパラメータを抽出
            data?.let {
                Log.d("MainActivity", "uri: $it")
                when (data.scheme) {
                    "openid4vp" -> {
                        handleVp(it)
                    }

                    "openid-credential-offer" -> {
                        handleOffer(it, navController)
                    }

                    "https" -> {
                        // App link
                        if (it.getQueryParameter("credential_offer").isNullOrEmpty()) {
                            handleVp(it)
                        } else {
                            handleOffer(it, navController)
                        }
                    }

                    else -> {
                        Log.d("MainActivity", "unknown scheme: ${data.scheme}")
                    }
                }
            }
        }

        // 生体認証の利用可能性をチェック
        // ディバイスの生体認証の状態ををチェックして設定されていない場合、設定画面に遷移
        val biometricStatus = BiometricUtil.checkBiometricAvailability(this)
        if (biometricStatus == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            BiometricUtil.createAlertDialog(this, enrollBiometricRequest)
        }
    }

    private fun handleOffer(uri: Uri, navController: androidx.navigation.NavController) {
        // ここでパラメータを処理
        val parameterValue = uri.getQueryParameter("credential_offer") // クエリパラメータの取得

        // credential_offerがある場合発行画面に遷移する
        if (!parameterValue.isNullOrEmpty()) {
            val bundle = Bundle().apply {
                putString("parameterValue", parameterValue)
            }
            navController.navigate(R.id.action_to_confirmation, bundle)
        }
    }

    private fun handleVp(uri: Uri) {
        val newIntent = Intent(this, TokenSharingActivity::class.java).apply {
            putExtra("siopRequest", uri.toString())
            putExtra("index", -1)
        }
        startActivity(newIntent)
    }

    private var isLocking = false

    fun setIsLocking(value: Boolean) {
        isLocking = value
    }

    override fun onStop() {
        Log.d("MainActivity", "onStop")
        val dataStore = PreferencesDataStore(this)
        super.onStop()
        if (isFinishing) {
            Log.d("MainActivity", "App is finishing, using runBlocking")
            runBlocking {
                if (!isLocking) {
                    val currentTime = System.currentTimeMillis()
                    val lastSafeOnStopTime = dataStore.getLastSafeOnStopTime()
                    val timeSinceLastSafeOnStop = currentTime - lastSafeOnStopTime
                    if (timeSinceLastSafeOnStop > LOCK_THRESHOLD_MS) {
                        Log.d("MainActivity", "saveShouldLock:true")
                        dataStore.saveShouldLock(true)
                    }
                }
                dataStore.saveLastSafeOnStopTime(0)
            }
        } else { // バックグラウンド遷移時は非同期処理で問題なし
            lifecycleScope.launch {
                if (!isLocking) {
                    val currentTime = System.currentTimeMillis()
                    val lastSafeOnStopTime = dataStore.getLastSafeOnStopTime()
                    val timeSinceLastSafeOnStop = currentTime - lastSafeOnStopTime
                    if (timeSinceLastSafeOnStop > LOCK_THRESHOLD_MS) {
                        Log.d("MainActivity", "saveShouldLock:true")
                        dataStore.saveShouldLock(true)
                    }
                }
                dataStore.saveLastSafeOnStopTime(0)
            }
        }
    }

    override fun onResume() {
        val dataStore = PreferencesDataStore(this)
        super.onResume()
        lifecycleScope.launch {
            if (isLocking) {
                isLocking = false
                dataStore.saveLastUnlockTime(System.currentTimeMillis())
            }
            val lastUnlockTime = dataStore.getLastUnlockTime()
            val currentTime = System.currentTimeMillis()
            val timeSinceLastUnlock = currentTime - lastUnlockTime

            val shouldLock = dataStore.getShouldLock()
            Log.d("MainActivity", "getShouldLock:$shouldLock")
            if (shouldLock && timeSinceLastUnlock > LOCK_THRESHOLD_MS) {
                // shouldLock = false
                dataStore.saveShouldLock(false)
                isLocking = true
                startActivity(Intent(this@MainActivity, LockScreenActivity::class.java))
            }
        }
    }
}