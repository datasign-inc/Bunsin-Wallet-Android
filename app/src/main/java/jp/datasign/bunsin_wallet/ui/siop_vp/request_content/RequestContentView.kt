package jp.datasign.bunsin_wallet.ui.siop_vp.request_content

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import jp.datasign.bunsin_wallet.R
import jp.datasign.bunsin_wallet.model.CertificateInfo
import jp.datasign.bunsin_wallet.model.ClientInfo
import jp.datasign.bunsin_wallet.model.RequestInfo
import jp.datasign.bunsin_wallet.ui.shared.composers.BodyText
import jp.datasign.bunsin_wallet.ui.shared.composers.FilledButton
import jp.datasign.bunsin_wallet.ui.shared.composers.SubHeadLineText
import jp.datasign.bunsin_wallet.ui.shared.composers.Title2Text
import jp.datasign.bunsin_wallet.ui.shared.composers.Title3Text
import jp.datasign.bunsin_wallet.ui.shared.composers.Verifier

val labelMap = mapOf(0 to "偽", 1 to "真", 2 to "どちらでも")

@Composable
fun RequestContentView(
    viewModel: TokenSharingViewModel,
    linkOpener: (url: String) -> Unit,
    closeHandler: () -> Unit,
    nextHandler: () -> Unit,
) {
    val clientInfo by viewModel.clientInfo.observeAsState()
    val errorMessage by viewModel.errorMessage.observeAsState()
    val requestInfo by viewModel.requestInfo.observeAsState()

    var hasExecuted by remember { mutableStateOf(false) }

    if (errorMessage != null) {
        AuthRequestError(error = errorMessage!!, onClick = closeHandler)
    } else if (clientInfo !== null && requestInfo != null) {
        if (requestInfo!!.responseType == "id_token") {
            if (!hasExecuted) {
                hasExecuted = true
                nextHandler()
            }
        } else {
            RequestContent(
                requestInfo = requestInfo!!,
                linkOpener = linkOpener,
                nextHandler = nextHandler
            )
        }
    } else {
        Loading()
    }
}

@Composable
fun Loading() {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BodyText("Loading...", modifier = Modifier.padding(8.dp))
    }
}

@Composable
fun AuthRequestError(error: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BodyText(error, modifier = Modifier.padding(8.dp))
        FilledButton("Close", onClick = onClick)
    }
}

@Composable
fun RequestContent(
    requestInfo: RequestInfo,
    linkOpener: (url: String) -> Unit,
    nextHandler: () -> Unit
) {
    Column(modifier = Modifier.fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Title2Text(requestInfo.title, modifier = Modifier.padding(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top=16.dp, bottom = 16.dp, start = 40.dp, end = 40.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.icon_art),
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .padding(8.dp)
                )
                Image(
                    painter = painterResource(R.drawable.arrow),
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .padding(8.dp)
                )
                AsyncImage(
                    model = requestInfo.clientInfo.logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .padding(8.dp)
                )
            }
            Title3Text("署名をする内容", modifier = Modifier.padding(8.dp))

            SubHeadLineText(
                "URL: ${requestInfo.url}",
                modifier = Modifier.padding(start = 8.dp)
            )
            SubHeadLineText(
                "真偽ステータス: ${labelMap[requestInfo.boolValue]}",
                modifier = Modifier.padding(start = 8.dp)
            )
            SubHeadLineText(
                "コメント: ${requestInfo.comment}",
                modifier = Modifier.padding(start = 8.dp)
            )

            SubHeadLineText(
                "提供先組織情報 ",
                modifier = Modifier.padding(start = 8.dp, top = 16.dp)
            )
            Verifier(requestInfo.clientInfo, linkOpener = linkOpener)
        }
        FilledButton("次へ", modifier = Modifier.padding(8.dp), onClick = nextHandler)
    }
}


@Preview(showBackground = true)
@Composable
fun PreviewRequestContent() {
    val requestInfo = RequestInfo(
        title = "真偽情報に署名を行い、その情報をBoolcheckに送信します",
        boolValue = 1,
        comment = "このXアカウントはXXX本人のものです",
        url = "https://example.com",
        clientInfo = clientInfo
    )
    RequestContent(requestInfo, linkOpener = {}) {
        // nop
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewLoading() {
    Loading()
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewLoading2() {
    Loading()
}

@Preview(showBackground = true)
@Composable
fun PreviewAuthRequestError() {
    AuthRequestError("Some error occurred") {}
}

val issuerCertInfo = CertificateInfo(
    domain = "",
    organization = "Amazon",
    country = "US",
    state = "",
    locality = "",
    street = "",
    email = ""
)
val certInfo = CertificateInfo(
    domain = "boolcheck.com",
    organization = "datasign.inc",
    country = "JP",
    state = "Tokyo",
    locality = "Sinzyuku-ku",
    street = "",
    email = "by-dev@datasign.jp",
    issuer = issuerCertInfo
)
val clientInfo = ClientInfo(
    name = "Boolcheck",
    certificateInfo = certInfo,
    tosUrl = "https://datasign.jp/tos",
    policyUrl = "https://datasign.jp/policy"
)
