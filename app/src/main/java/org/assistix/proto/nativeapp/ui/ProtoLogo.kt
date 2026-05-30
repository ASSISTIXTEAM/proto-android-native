package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.assistix.proto.nativeapp.R

@Composable
fun ProtoLogo(
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    contentDescription: String = UiStrings.appName,
) {
    Image(
        painter = painterResource(R.drawable.proto_logo),
        contentDescription = contentDescription,
        modifier = modifier.height(height).widthIn(max = 140.dp),
        contentScale = ContentScale.Fit,
    )
}
