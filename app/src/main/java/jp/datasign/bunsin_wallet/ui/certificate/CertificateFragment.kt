package jp.datasign.bunsin_wallet.ui.certificate

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import jp.datasign.bunsin_wallet.R
import jp.datasign.bunsin_wallet.databinding.FragmentCertificateBinding
import jp.datasign.bunsin_wallet.datastore.CredentialData
import jp.datasign.bunsin_wallet.datastore.CredentialDataStore
import jp.datasign.bunsin_wallet.datastore.PreferencesDataStore
import jp.datasign.bunsin_wallet.ui.credential_detail.CredentialDetailFragment
import jp.datasign.bunsin_wallet.ui.shared.CredentialSharingViewModel
import jp.datasign.bunsin_wallet.utils.DisplayUtil
import jp.datasign.bunsin_wallet.utils.MetadataUtil
import jp.datasign.bunsin_wallet.utils.QRCodeScannerUtil
import jp.datasign.bunsin_wallet.vci.CredentialIssuerMetadata
import jp.datasign.bunsin_wallet.vci.CredentialOffer
import kotlinx.coroutines.launch
import java.net.URLDecoder

class CertificateFragment : Fragment() {
    companion object {
        val tag = CredentialDetailFragment::class.simpleName
    }

    private var _binding: FragmentCertificateBinding? = null
    private val sharedViewModel by activityViewModels<CredentialSharingViewModel>()

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var scanLauncher: ActivityResultLauncher<ScanOptions>
    private lateinit var qrCodeUtil: QRCodeScannerUtil

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        val dataStore = PreferencesDataStore(requireContext())

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                qrCodeUtil.launchQRCodeScanner()
            } else {
                Toast.makeText(context, "カメラの権限が必要です", Toast.LENGTH_SHORT).show()
            }
        }

        scanLauncher = registerForActivityResult(ScanContract()) { result ->
            if (result.contents == null) {
                Toast.makeText(context, "Cancelled", Toast.LENGTH_LONG).show()
            } else {
                // QRコードの内容を解析
                val credentialOffer = parseQRCodeData(result.contents)
                // QRコードの中身がcredentialOffer
                if (isCredentialOfferValid(credentialOffer)) {
                    // 有効なcredentialOfferの場合
                    val bundle = bundleOf("parameterValue" to credentialOffer)
                    findNavController().navigate(R.id.confirmationFragment, bundle)
                } else {
                    // 無効なcredentialOfferの場合
                    Toast.makeText(context, "Invalid Credential Offer", Toast.LENGTH_LONG).show()
                }
            }
        }

        qrCodeUtil =
            QRCodeScannerUtil(requireContext(), scanLauncher, requestPermissionLauncher)

        val credentialDataStore = CredentialDataStore.getInstance(requireContext())
        val dashboardViewModel =
            ViewModelProvider(this, CertificateViewModelFactory(credentialDataStore)).get(
                CertificateViewModel::class.java
            )

        _binding = FragmentCertificateBinding.inflate(inflater, container, false)
        val root: View = binding.root

        if (sharedViewModel.presentationDefinition.value != null) {
            binding.fabAddCertificate.visibility = View.GONE
        }

        DisplayUtil.setFragmentTitle(
            activity as? AppCompatActivity,
            getString(R.string.title_certificate)
        )

        val textView: TextView = binding.textCertificate
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        dashboardViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }

        binding.imgAddCertificate.setOnClickListener {
            lifecycleScope.launch {
                dataStore.saveLastSafeOnStopTime(System.currentTimeMillis())
            }
            if (qrCodeUtil.hasCameraPermission()) {
                qrCodeUtil.launchQRCodeScanner()
            } else {
                qrCodeUtil.requestCameraPermission()
            }
        }

        binding.fabAddCertificate.setOnClickListener {
            lifecycleScope.launch {
                dataStore.saveLastSafeOnStopTime(System.currentTimeMillis())
            }
            if (qrCodeUtil.hasCameraPermission()) {
                qrCodeUtil.launchQRCodeScanner()
            } else {
                qrCodeUtil.requestCameraPermission()
            }
        }

        dashboardViewModel.credentialDataList.observe(viewLifecycleOwner) { schema ->
            val itemsList = schema?.itemsList
            if (!itemsList.isNullOrEmpty()) {
                binding.textCertificate.visibility = View.GONE
                binding.imgAddCertificate.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE

                // Adapterをセット
                val filteredItemsList =
                    itemsList?.filter { filterCredential(it) }
                val adapter = CredentialAdapter(filteredItemsList ?: emptyList())
                binding.recyclerView.layoutManager = LinearLayoutManager(context)
                binding.recyclerView.adapter = adapter
            } else {
                binding.textCertificate.visibility = View.VISIBLE
                binding.imgAddCertificate.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            }
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (sharedViewModel.presentationDefinition.value != null) {
            // MenuProviderを追加
            val activity = requireActivity()
            val menuProvider = CertificateFragmentMenuProvider(this, activity.menuInflater)
            activity.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun isCredentialOfferValid(credentialOfferJson: String): Boolean {
        return try {
            Log.d("AddItemBottomSheetFragment", "src url = $credentialOfferJson")
            val mapper = jacksonObjectMapper().apply {
                propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
            }
            val mappedCredentialOffer: CredentialOffer = mapper.readValue(credentialOfferJson)
            Log.d("AddItemBottomSheetFragment", "mappedCredentialOffer = $mappedCredentialOffer")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun parseQRCodeData(qrCodeContents: String): String {
        val decodedContents = URLDecoder.decode(qrCodeContents, "UTF-8")
        val queryStartIndex = decodedContents.indexOf('?')
        if (queryStartIndex == -1 || queryStartIndex >= decodedContents.length - 1) {
            return "ERROR"
        }

        val queryParams = decodedContents.substring(queryStartIndex + 1)
        val keyValuePairs = queryParams.split("&").associate {
            val keyValue = it.split("=")
            if (keyValue.size >= 2) keyValue[0] to keyValue[1] else keyValue[0] to ""
        }

        return keyValuePairs["credential_offer"] ?: "ERROR"
    }
}

fun filterCredential(
    credential: CredentialData,
): Boolean {
    val format = credential.format
    println("format: $format")
    val types =
        MetadataUtil.extractTypes(format, credential.credential)
    if (types.contains("CommentCredential")) {
        return false
    }
    return true
}

class CertificateViewModelFactory(private val credentialDataStore: CredentialDataStore) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CertificateViewModel::class.java)) {
            return CertificateViewModel(credentialDataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class CredentialAdapter(private val credentials: List<jp.datasign.bunsin_wallet.datastore.CredentialData>) :
    RecyclerView.Adapter<CredentialAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.credential_name)
        val logoImageView: ImageView = view.findViewById(R.id.credential_logo)
        val cardView: CardView = view.findViewById(R.id.card_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_verifiable_credential, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        // themeの中からデフォルトカラーを取得する
        val typedValue = TypedValue()
        val theme = holder.itemView.context.theme
        theme.resolveAttribute(R.attr.colorSecondaryVariant, typedValue, true)
        val defaultCardColor = typedValue.data

        val credential = credentials[position]
        val objectMapper = jacksonObjectMapper()
        val metadata: CredentialIssuerMetadata = objectMapper.readValue(
            credential.credentialIssuerMetadata,
            CredentialIssuerMetadata::class.java
        )
        val format = credential.format
        val types =
            MetadataUtil.extractTypes(format, credential.credential)
        val credentialSupported = metadata.credentialConfigurationsSupported[types.firstOrNull()]
        val displayData = credentialSupported!!.display!!.firstOrNull()

        //  display.text_colorがある場合はクレデンシャル名表示にそれを適用
        holder.nameTextView.text = displayData!!.name
        displayData.textColor?.takeIf { it.isNotEmpty() }?.let { colorCode ->
            val color = Color.parseColor(colorCode)
            holder.nameTextView.setTextColor(color)
        }

        val hasBackgroundImage = displayData.backgroundImage != null
        val hasBackgroundColor = displayData.backgroundColor != null
        val hasLogo = displayData.logo?.uri?.isNotEmpty() == true
        // 以下の条件で出し分けを行う
        //  | #   | backgroundImage | backgroundColor | logo | 対応                                                                       |
        //  | --- | --------------- | --------------- | ---- | -------------------------------------------------------------------------- |
        //  | 1   | 無              | 無              | 無   | 予めアプリに組み込んだ画像、または色でカードを表示する。デフォルトロゴを表示する。 |
        //  | 2   | 無              | 無              | 有   | 上記にロゴを重ねる                                                         |
        //  | 3   | 無              | 有              | 無   | 指定の背景色でカードを描画する。                                           |
        //  | 4   | 無              | 有              | 有   | 指定の背景色でカードを描画し、ロゴを表示する。                             |
        //  | 5   | 有              | 無              | 無   | backgroundImage を用いる。背景色とロゴは使わない                          |
        //  | 6   | 有              | 無              | 有   | backgroundImage を用いる。ロゴを表示する。                                |
        //  | 7   | 有              | 有              | 無   | backgroundImage を用いる。背景色は使わない。                              |
        //  | 8   | 有              | 有              | 有   | backgroundImage とロゴを使う。背景色は使わない。                          |
        // ロゴの処理
        if (hasLogo) {
            Glide.with(holder.itemView.context)
                .load(displayData.logo) // logo is now a URL
                .into(holder.logoImageView)
        } else if (!hasBackgroundImage) {
            val drawable = ContextCompat.getDrawable(holder.itemView.context, R.drawable.icon_art)
            holder.logoImageView.setImageDrawable(drawable)
            holder.logoImageView.visibility = View.VISIBLE
        }


        // 背景画像の処理
        // 背景画像はurlから取得してイメージにする
        if (hasBackgroundImage) {
            Glide.with(holder.itemView.context)
                .asBitmap()
                .load(displayData.backgroundImage?.uri) // URLから背景画像をロード
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?,
                    ) {
                        val drawable = BitmapDrawable(holder.itemView.context.resources, resource)
                        holder.cardView.background = drawable
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                    }
                })
            holder.nameTextView.visibility = View.GONE
        } else if (hasBackgroundColor) {
            holder.cardView.setCardBackgroundColor(Color.parseColor(displayData.backgroundColor))
        } else {
            holder.cardView.setCardBackgroundColor(defaultCardColor)
        }

        holder.cardView.setOnClickListener {
            val action = CertificateFragmentDirections.actionToCredentialDetail(credential.id)
            it.findNavController().navigate(action)
        }
    }

    private fun Bundle.putCredentialData(
        key: String,
        credentialData: jp.datasign.bunsin_wallet.datastore.CredentialData,
    ) {
        putByteArray(key, credentialData.toByteArray())
    }

    override fun getItemCount() = credentials.size
}
