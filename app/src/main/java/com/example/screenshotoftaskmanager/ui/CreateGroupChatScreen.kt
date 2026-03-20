@file:Suppress("UNUSED_VALUE")
package com.example.screenshotoftaskmanager.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.screenshotoftaskmanager.CloudChatManager
import com.example.screenshotoftaskmanager.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupChatScreen(navController: NavController) {
    val context = LocalContext.current

    var groupName by remember { mutableStateOf("") }
    var selectedUserIds by remember { mutableStateOf(setOf<String>()) }
    var isLoading by remember { mutableStateOf(false) }
    var availableUsers by remember { mutableStateOf<List<User>>(emptyList()) }

    LaunchedEffect(Unit) {
        availableUsers = DataSource.conversations
            .filter { conversation ->
                // ✅ 只提取私聊（一对一），不包括群聊
                conversation.chatType == "private" && conversation.otherUserUid.isNotBlank()
            }
            .map { conversation ->
                User(
                    uid = conversation.otherUserUid,
                    username = conversation.name
                )
            }
            .distinctBy { it.uid }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创建群聊") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("群聊名称") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "已选择 ${selectedUserIds.size} 个好友",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(availableUsers) { user ->
                    UserSelectionItem(
                        user = user,
                        isSelected = user.uid in selectedUserIds,
                        onSelectionChange = { isSelected ->
                            if (isSelected) {
                                selectedUserIds = selectedUserIds + user.uid
                            } else {
                                selectedUserIds = selectedUserIds - user.uid
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (groupName.isBlank()) {
                        Toast.makeText(context, "请输入群聊名称", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (selectedUserIds.isEmpty()) {
                        Toast.makeText(context, "请至少选择一个好友", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLoading = true

                    CloudChatManager.createGroupChat(
                        groupName = groupName,
                        memberUids = selectedUserIds.toList(),
                        onComplete = { success, message ->
                            isLoading = false
                            if (success) {
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            } else {
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !isLoading && groupName.isNotBlank() && selectedUserIds.isNotEmpty()
            ) {
                Text(if (isLoading) "创建中..." else "创建群聊")
            }
        }
    }
}

@Composable
fun UserSelectionItem(
    user: User,
    isSelected: Boolean,
    onSelectionChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelectionChange(!isSelected) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChange,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = user.username,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

