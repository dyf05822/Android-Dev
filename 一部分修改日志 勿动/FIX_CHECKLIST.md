# 修复清单：群聊名称显示问题

## 核心修复

### 1️⃣ DataSource.replaceConversations (关键修复)
```kotlin
// ✅ 新的 key 策略：群聊用 "group_${chatId}"，私聊用 "private_${otherUserUid}"
val existingMap = conversations.associateBy { conversation ->
    if (conversation.chatType == "group") {
        "group_${conversation.chatId}"
    } else {
        "private_${conversation.otherUserUid}"
    }
}
```

### 2️⃣ DataSource.getOrCreateConversation (严格检查)
```kotlin
// ✅ 添加 chatType 检查，避免群聊被识别为私聊
val groupConversation = conversations.find { 
    it.chatId == otherUserUid && it.chatType == "group" 
}
val privateConversation = conversations.find { 
    it.otherUserUid == otherUserUid && it.chatType == "private" 
}
```

### 3️⃣ ConversationListScreen 列表 key (保持一致)
```kotlin
// ✅ 与 replaceConversations 使用相同的 key 策略
key = { conversation ->
    if (conversation.chatType == "group") {
        "group_${conversation.chatId}"
    } else {
        "private_${conversation.otherUserUid}"
    }
}
```

### 4️⃣ CreateGroupChatScreen 成员提取 (只要私聊)
```kotlin
// ✅ 过滤出只有私聊的成员，不包括群聊
availableUsers = DataSource.conversations
    .filter { conversation ->
        conversation.chatType == "private" && 
        conversation.otherUserUid.isNotBlank()
    }
    .map { conversation ->
        User(uid = conversation.otherUserUid, username = conversation.name)
    }
    .distinctBy { it.uid }
```

### 5️⃣ CloudChatManager.listenMyConversations (增强日志)
```kotlin
// ✅ 添加详细日志追踪群聊加载
Log.d("CloudChatManager", "✅ 加载群聊：chatId=${chat.chatId}, groupName=$groupName, " +
    "participants=${chat.participants.size}人, chatType=${chat.chatType}, owner=${chat.owner}")
```

## 测试步骤

### 快速验证
1. **创建群聊** "测试群"，邀请成员 A、B
2. **检查列表**：应显示 "测试群"（不是 A 或 B）
3. **查看日志**：`logcat | grep "✅ 加载群聊"`
4. **进入群聊后返回**：群聊名称应保持不变

### 压力测试
1. 创建多个群聊，名称相同但成员不同
2. 创建与群聊同名的私聊
3. 多次进出会话，检查是否混乱

## 日志对照表

| 现象 | 日志输出 | 判断 |
|------|--------|------|
| ✅ 正常群聊 | `✅ 加载群聊：groupName=我的群, chatType=group` | 正确 |
| ❌ 群聊名错误 | `✅ 加载群聊：groupName=xiaoming` | 问题未解决 |
| ❌ 被识别为私聊 | `✅ 加载私聊：username=我的群` | 问题未解决 |
| ✅ 缓存合并正确 | `合并会话 群聊 [我的群]: key=group_xxx` | 正确 |
| ❌ 缓存混乱 | `合并会话 私聊 [我的群]: key=group_xxx` | 问题未解决 |

## 如果问题仍然存在

### 1. 检查 Firestore 数据
```
路径：chats/{groupId}
必须确保：
- chatType = "group" (不是 "private")
- groupName = "你输入的群名" (不是成员名)
- participants = [uid1, uid2, ...] (不为空)
```

### 2. 查看完整日志链路
```
搜索关键词：
- "创建群聊成功" - 检查群ID和名称
- "加载群聊" - 检查从云端加载的数据
- "合并会话" - 检查缓存合并过程
- "通过 chatId 找到群聊" - 检查查询逻辑
```

### 3. 强制刷新
```
- 清除应用数据后重新登录
- 检查是否仍有问题
```

## 修改的文件列表

1. `CloudChatManager.kt` - 增强日志和验证
2. `DataSource.kt` - 核心修复：key 策略和查询逻辑
3. `ConversationListScreen.kt` - 列表 key 和日志
4. `CreateGroupChatScreen.kt` - 成员提取过滤

---

**修复时间**：2026-03-20
**修复人员**：GitHub Copilot
**优先级**：🔴 紧急（影响群聊核心功能）

