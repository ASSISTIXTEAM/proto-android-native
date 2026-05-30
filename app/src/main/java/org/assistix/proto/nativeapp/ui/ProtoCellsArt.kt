package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hive
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import org.assistix.proto.nativeapp.R

@Composable
fun ProtoCellsArt(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val resId =
        remember {
            ctx.resources.getIdentifier("proto_cells_icon", "drawable", ctx.packageName)
        }
    if (resId != 0) {
        Image(
            painter = painterResource(resId),
            contentDescription = null,
            modifier = modifier,
        )
    } else {
        Icon(
            Icons.Default.Hive,
            contentDescription = null,
            modifier = modifier,
            tint = ProtoOrange,
        )
    }
}
