package jp.datasign.bunsin_wallet.ui.recipient

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.datasign.bunsin_wallet.R
import jp.datasign.bunsin_wallet.databinding.FragmentSharedClaimDetailBinding
import jp.datasign.bunsin_wallet.datastore.Claim
import jp.datasign.bunsin_wallet.ui.shared.composers.decodeBase64ToBitmap
import jp.datasign.bunsin_wallet.ui.shared.composers.isBase64Image
import jp.datasign.bunsin_wallet.utils.DisplayUtil

class SharedClaimDetailFragment : Fragment() {

    private var _binding: FragmentSharedClaimDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentSharedClaimDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity()
        val menuProvider = SharedClaimDetailFragmentMenuProvider(this, activity.menuInflater)
        activity.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val recipientViewModel =
            ViewModelProvider(requireActivity())[RecipientViewModel::class.java]

        recipientViewModel.targetHistory.observe(viewLifecycleOwner) { history ->
            if (history != null) {
                val timestampInString = timestampToString(history.createdAt)
                DisplayUtil.setFragmentTitle(
                    activity as? AppCompatActivity, getString(
                        R.string.timing_of_claim_sharing, timestampInString
                    )
                )

                val rpTextView = view.findViewById<TextView>(R.id.claim_recipient)
                rpTextView.text = getString(R.string.claim_recipient, history.rpName)

                val adapter = ClaimAdapter(history.claimsList)
                binding.sharedClaims.layoutManager = LinearLayoutManager(context)
                binding.sharedClaims.adapter = adapter
            } else {
                Log.d(tag, "unset target history!")
            }
        }
    }
}


class ClaimAdapter(
    private val claims: List<Claim>
) :
    RecyclerView.Adapter<ClaimAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val claimTitle: TextView = view.findViewById(R.id.claim_title)
        val claimPurpose: TextView = view.findViewById(R.id.claim_purpose)
        val claimValue: TextView = view.findViewById(R.id.claim_value)
        val imageValue: ImageView = view.findViewById(R.id.claim_image_value)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shared_claim, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val claim = claims[position]
        holder.claimTitle.text = claim.claimKey
        if (claim.purpose != "") {
            holder.claimPurpose.visibility = View.VISIBLE
            holder.claimPurpose.text = claim.purpose
        }
        if (claim.claimValue.isBase64Image()) {
            holder.claimValue.visibility = View.GONE
            holder.imageValue.visibility = View.VISIBLE
            holder.imageValue.setImageBitmap(claim.claimValue.decodeBase64ToBitmap())
        } else if (claim.claimValue != "") {
            holder.claimValue.visibility = View.VISIBLE
            holder.claimValue.text = claim.claimValue
        }
    }

    private fun Bundle.putCredentialSharingHistory(
        key: String,
        claim: String,
    ) {
        putByteArray(key, claim.toByteArray())
    }

    override fun getItemCount() = claims.size
}