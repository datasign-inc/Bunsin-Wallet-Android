package jp.datasign.bunsin_wallet.ui.shared.composers

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun Title2Text(value: String, modifier: Modifier) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black
    Text(
        value,
        style = TextStyle(
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            lineHeight = 28.sp
        ),
        modifier = modifier
    )
}

@Composable
fun Title3Text(value: String, modifier: Modifier) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black
    Text(
        value,
        style = TextStyle(
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            lineHeight = 25.sp
        ), modifier = modifier
    )
}

@Composable
fun BodyText(value: String, modifier: Modifier) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black
    Text(
        value,
        style = TextStyle(
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            color = textColor,
            lineHeight = 22.sp
        ), modifier = modifier
    )
}

@Composable
fun BodyEmphasizedText(value: String, modifier: Modifier) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black
    Text(
        value,
        style = TextStyle(
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            lineHeight = 22.sp
        ), modifier = modifier
    )
}

@Composable
fun CalloutText(value: String, modifier: Modifier = Modifier) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black
    Text(
        value,
        style = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = textColor,
            lineHeight = 21.sp
        ), modifier = modifier
    )
}

@Composable
fun SubHeadLineText(value: String, modifier: Modifier) {
    Text(
        value,
        style = TextStyle(
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = Color.Gray,
            lineHeight = 20.sp
        ), modifier = modifier
    )
}

@Composable
fun Caption1Text(value: String, modifier: Modifier) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.DarkGray
    Text(
        value,
        style = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = textColor,
            lineHeight = 16.sp
        ), modifier = modifier
    )
}

@Composable
fun FootnoteText(value: String, modifier: Modifier) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.LightGray else Color.Gray
    Text(
        value,
        style = TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = textColor,
            lineHeight = 18.sp
        ), modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewTitle2() {
    Title2Text("title2", modifier = Modifier.padding(4.dp))
}

@Preview(showBackground = true)
@Composable
fun PreviewTitle3() {
    Title3Text("title3", modifier = Modifier.padding(4.dp))
}

@Preview(showBackground = true)
@Composable
fun PreviewBody() {
    BodyText("Body", modifier = Modifier.padding(4.dp))
}

@Preview(showBackground = true)
@Composable
fun PreviewBodyEmphasized() {
    BodyEmphasizedText("Body", modifier = Modifier.padding(4.dp))
}

@Preview(showBackground = true)
@Composable
fun PreviewCallout() {
    CalloutText("Callout", modifier = Modifier.padding(4.dp))
}

@Preview(showBackground = true)
@Composable
fun PreviewSubHeadLine() {
    SubHeadLineText("SubHeadline", modifier = Modifier.padding(4.dp))
}

@Preview(showBackground = true)
@Composable
fun PreviewCaption1() {
    Caption1Text("Caption1", modifier = Modifier.padding(4.dp))
}

@Preview(showBackground = true)
@Composable
fun PreviewFootnote() {
    FootnoteText("Footnote", modifier = Modifier.padding(4.dp))
}
