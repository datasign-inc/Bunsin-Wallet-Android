package jp.datasign.bunsin_wallet.ui.siop_vp.shared_data_confirmation

import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.datasign.bunsin_wallet.ui.shared.composers.CalloutText
import jp.datasign.bunsin_wallet.ui.shared.composers.Claim
import jp.datasign.bunsin_wallet.ui.shared.composers.FilledButton
import jp.datasign.bunsin_wallet.ui.shared.composers.SharedData
import jp.datasign.bunsin_wallet.ui.shared.composers.SubHeadLineText
import jp.datasign.bunsin_wallet.ui.shared.composers.Title2Text
import jp.datasign.bunsin_wallet.ui.shared.composers.Verifier
import jp.datasign.bunsin_wallet.ui.shared.composers.getPreviewData
import jp.datasign.bunsin_wallet.model.RequestInfo
import jp.datasign.bunsin_wallet.ui.siop_vp.request_content.clientInfo
import jp.datasign.bunsin_wallet.utils.MetadataUtil
import jp.datasign.bunsin_wallet.utils.SDJwtUtil


val tag = "SharedDataConfirmationView"

@Composable
fun SharedDataConfirmationView(
    viewModel: SharedDataConfirmationViewModel,
    linkOpener: (url: String) -> Unit,
    sendHandler: (selected: List<SDJwtUtil.Disclosure>) -> Unit
) {
    val requestInfo by viewModel.requestInfo.observeAsState()
    val subJwk by viewModel.subJwk.observeAsState()
    val havingClaims by viewModel.claims.observeAsState()
    if (subJwk != null && requestInfo != null && havingClaims != null) {
        val claims = if (havingClaims!!.isNotEmpty()) {
            val displayMap = viewModel.displayMap
            val order = viewModel.claimOrder
            val claims = havingClaims!!.map {
                val key = it.data.key
                val displays = displayMap[key]
                val label =
                    MetadataUtil.getLocalizedDisplayName(displays!!).takeUnless { it.isNullOrEmpty() }
                        ?: key!!
                Claim(data = it.data, label = label, optional = it.optional, checked = !it.optional)
            }
            claims.sortedBy { claim ->
                order.indexOf(claim.data.key).takeIf { it != -1 } ?: Int.MAX_VALUE
            }
        } else {
            listOf()
        }
        SharedDataConfirmation(
            subJwk!!,
            claims,
            requestInfo = requestInfo!!,
            linkOpener = linkOpener,
            sendHandler = sendHandler
        )
    }
}

@Composable
fun SharedDataConfirmation(
    id: String,
    claims: List<Claim>,
    requestInfo: RequestInfo,
    linkOpener: (url: String) -> Unit,
    sendHandler: (selected: List<SDJwtUtil.Disclosure>) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .padding(0.dp)
        ) {
            Title2Text(
                "提供情報の確認",
                modifier = Modifier
                    .padding(start = 8.dp, top = 16.dp, bottom = 16.dp)
                    .testTag("title")
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 0.dp, end = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                ) {
                    SubHeadLineText("Bunsin walletで作成したID", modifier = Modifier.padding(0.dp))
                    CalloutText(id, modifier = Modifier.padding(top = 8.dp))
                }
                Text(
                    "※必須",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.Red,
                        lineHeight = 21.sp
                    ),
                    modifier = Modifier.padding(8.dp)
                )
            }
            SharedData(claims)
            SubHeadLineText(
                "提供先組織情報",
                modifier = Modifier.padding(start = 8.dp, top = 16.dp)
            )
            Verifier(requestInfo.clientInfo, linkOpener = linkOpener)
        }
        FilledButton("送信する", modifier = Modifier.padding(8.dp)) {
            val selected = claims.filter { it.checked }.map { it.data }
            selected.forEach {
                Log.d(tag, "selected:${it.key}")
            }
            sendHandler(selected)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSharedDataConfirmation() {
    val claims = getPreviewData(Color.Red)
    val requestInfo = RequestInfo(
        title = "真偽情報に署名を行い、その情報をBoolcheckに送信します",
        boolValue = 1,
        comment = "このXアカウントはXXX本人のものです",
        url = "https://example.com",
        clientInfo = clientInfo
    )
    SharedDataConfirmation(
        id = "xxx",
        claims = claims,
        requestInfo = requestInfo,
        linkOpener = { url -> }) { }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewSharedDataConfirmation2() {
    val claims = getPreviewData(Color.Blue)
    val requestInfo = RequestInfo(
        title = "真偽情報に署名を行い、その情報をBoolcheckに送信します",
        boolValue = 1,
        comment = "このXアカウントはXXX本人のものです",
        url = "https://example.com",
        clientInfo = clientInfo
    )
    SharedDataConfirmation(
        id = "xxx",
        claims = claims,
        requestInfo = requestInfo,
        linkOpener = { url -> }) { }
}

@Preview(showBackground = true)
@Composable
fun PreviewSharedDataConfirmation3() {
    val claims = listOf<Claim>()
    val requestInfo = RequestInfo(
        title = "真偽情報に署名を行い、その情報をBoolcheckに送信します",
        boolValue = 1,
        comment = "このXアカウントはXXX本人のものです",
        url = "https://example.com",
        clientInfo = clientInfo
    )
    SharedDataConfirmation(
        id = "xxx",
        claims = claims,
        requestInfo = requestInfo,
        linkOpener = { url -> }) { }
}

