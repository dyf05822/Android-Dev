# 群聊名称显示问题修复总结

## 问题描述
用户反馈：创建群聊后，群聊名称在列表中显示为**第一个被选中成员的名字**，而不是用户输入的群聊名称。例如：
- 邀请成员 B、C → 群聊显示为"B"
- 邀请成员 C、D → 群聊显示为"C"
- 返回列表后，群聊变成了"xiaoming"的私聊

## 根本原因分析

根本原因是多个地方的 UI 和业务逻辑仍然**按私聊处理群聊**，而不是按群聊逻辑来处理。

### 具体问题点：

1. **会话列表 Key 重复** - 群聊和私聊可能使用相同的 key，导致覆盖
2. **会话缓存合并逻辑不严谨** - 使用 `name` 作为 fallback key，导致群聊被同名私聊覆盖
3. **创建群聊成员列表提取错误** - 从 conversations 中提取成员时，群聊的 `otherUserUid` 为空但 `name` 是群名
4. **会话详情页查询逻辑不严谨** - 没有严格区分群聊和私聊的查询条件

## 修复方案

### 1. 强化会话缓存合并策略 (DataSource.replaceConversations)

**修改前：**
```kotlin
val existingMap = conversations.associateBy { 
    it.chatId.ifBlank { it.otherUserUid.ifBlank { it.name } }
}
```

**修改后：**
```kotlin
val existingMap = conversations.associateBy { conversation ->
    if (conversation.chatType == "group") {
        "group_${conversation.chatId}"  // 群聊用 group 前缀
    } else {
        "private_${conversation.otherUserUid}"  // 私聊用 private 前缀
    }
}
```

**效果：** 即使群聊和私聊的 name 相同，也不会互相覆盖，因为使用了不同的 key 前缀。

### 2. 更新会话列表 LazyColumn Key (ConversationListScreen)

**修改前：**
```kotlin
key = { conversation -> 
    conversation.chatId.ifBlank { 
        conversation.otherUserUid.ifBlank { conversation.name } 
    } 
}
```

**修改后：**
```kotlin
key = { conversation ->
    if (conversation.chatType == "group") {
        "group_${conversation.chatId}"
    } else {
        "private_${conversation.otherUserUid}"
    }
}
```

**效果：** Compose LazyColumn 的 key 与缓存 key 保持一致，避免重组时的混乱。

### 3. 修复创建群聊时成员列表提取 (CreateGroupChatScreen)

**修改前：**
```kotlin
availableUsers = DataSource.conversations
    .map { conversation ->
        User(
            uid = conversation.otherUserUid,
            username = conversation.name
        )
    }
    .distinctBy { it.uid }
```

**修改后：**
```kotlin
availableUsers = DataSource.conversations
    .filter { conversation ->
        // ✅ 只提取私聊（一对一），不包括群聊
        conversation.chatType == "private" && 
        conversation.otherUserUid.isNotBlank()
    }
    .map { conversation ->
        User(
            uid = conversation.otherUserUid,
            username = conversation.name
        )
    }
    .distinctBy { it.uid }
```

**效果：** 成员列表中不再混入群聊信息，避免用户误选。

### 4. 强化会话详情页查询逻辑 (DataSource.getOrCreateConversation)

**修改前：**
```kotlin
val groupConversation = conversations.find { it.chatId == otherUserUid }
val privateConversation = conversations.find { it.otherUserUid == otherUserUid }
```

**修改后：**
```kotlin
val groupConversation = conversations.find { 
    it.chatId == otherUserUid && it.chatType == "group" 
}
val privateConversation = conversations.find { 
    it.otherUserUid == otherUserUid && it.chatType == "private" 
}
```

**效果：** 通过明确的 chatType 检查，避免群聊被误识别为私聊。

### 5. 增强 CloudChatManager 日志和验证

在 `listenMyConversations` 中添加详细日志：
- 记录每个加载的群聊/私聊的 chatId、name、groupName、chatType
- 警告群聊的 groupName 为空的情况
- 追踪会话加载过程中的任何异常

## 验证方案

### 测试场景 1：创建单个群聊
1. 创建群聊 "我的群" (邀请成员 A、B)
2. **预期：** 列表显示 "我的群"（不是 A 或 B）
3. 检查 Logcat：应看到 "✅ 加载群聊：groupName=我的群"

### 测试场景 2：创建多个同名群聊
1. 创建群聊 "群1" (邀请 A、B)
2. 创建群聊 "群1" (邀请 C、D)  
3. **预期：** 列表显示两个 "群1"（通过 chatId 区分）

### 测试场景 3：群聊返回列表后不被私聊覆盖
1. 创建群聊 "My Group"
2. 之前有与 "MyGroup" 用户的私聊
3. 进入群聊后返回列表
4. **预期：** 群聊仍显示 "My Group"（不变成私聊名）

## 日志输出示例

修复后，正常情况下 Logcat 应该看到：

```
CloudChatManager: ✅ 加载群聊：chatId=group_1773936110174_778, groupName=我的群, participants=3人, chatType=group, owner=w4pGX3VMIrf0aL4CxMAPWjly5GC3
DataSource: 合并会话 群聊 [我的群]: key=group_group_1773936110174_778, 是否已存在=false
ConversationListScreen: 群聊 [我的群]: chatId=group_1773936110174_778, otherUserUid=
```

如果看到以下日志，说明问题仍存在：
- "✅ 加载群聊：groupName=xiaoming" - 群名被设成了用户名
- "私聊 [我的群]" - 群聊被识别成了私聊

## 相关文件修改

1. **CloudChatManager.kt**
   - 增强 listenMyConversations 中群聊加载的日志和验证

2. **DataSource.kt**
   - replaceConversations：改用 "group_${chatId}" 和 "private_${otherUserUid}" 作为 key
   - getOrCreateConversation：添加 chatType 检查

3. **ConversationListScreen.kt**
   - items key 改为与 replaceConversations 相同的策略
   - 添加 LaunchedEffect 日志追踪

4. **CreateGroupChatScreen.kt**
   - availableUsers 只从私聊中提取

## 后续改进方向

1. **UI 层面** - 在详情页标题中明确区分群聊和私聊
2. **数据层面** - 确保 Firestore 中群聊的 chatType、groupName 字段始终正确填充
3. **测试** - 添加单元测试验证会话缓存合并逻辑

