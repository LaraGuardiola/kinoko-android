package dev.kinoko.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kinoko.app.data.Approval

/** A call the agent waits on, above the input: what it wants to run, why it asks, and the answers.
 *  The first answer from anywhere wins; when another client answers, the card simply goes. */
@Composable
fun ApprovalCard(approval: Approval, onAnswer: (allow: Boolean, remember: Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).background(Kinoko.panel, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text("🔐 wants to run", fontWeight = FontWeight.SemiBold, color = Kinoko.cream)
        Text(approval.reason, style = MaterialTheme.typography.labelSmall, color = Kinoko.muted, modifier = Modifier.padding(top = 2.dp))
        Text(
            approval.command,
            fontFamily = Mono,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = Kinoko.cream,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).background(Kinoko.night, RoundedCornerShape(10.dp)).heightIn(max = 140.dp)
                .verticalScroll(rememberScrollState()).padding(10.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onAnswer(true, false) }, colors = ButtonDefaults.buttonColors(containerColor = Kinoko.green, contentColor = Kinoko.night)) { Text("Allow") }
            OutlinedButton(onClick = { onAnswer(false, false) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Kinoko.softRed)) { Text("Deny") }
            TextButton(onClick = { onAnswer(true, true) }, colors = ButtonDefaults.textButtonColors(contentColor = Kinoko.cream)) { Text("For this session") }
        }
    }
}
