# 第一步完成总结 ✅ 准备工作：理解数据结构和依赖

## 执行时间
2026-03-17

## 完成内容

### 1️⃣ 创建 Models.kt - 定义数据结构
📁 **文件路径**: `app/src/main/java/com/example/screenshotoftaskmanager/Models.kt`

#### 新增数据类：

**Chat 类** - 聊天会话
```kotlin
data class Chat(
    var chatId: String = "",                    // 聊天ID
    var participants: List<String> = emptyList(),    // 参与者UID列表
    var lastMessage: String = "",              // 最后一条消息
    var lastTimestamp: Long = 0L,              // 最后消息时间
    var lastSenderId: String = ""              // 最后消息发送者
)
```
💡 **用途**: 存储到 Firestore `chats/{chatId}` 集合

**Message 类** - 单条消息
```kotlin
data class Message(
    var senderId: String = "",      // 发送者UID
    var receiverId: String = "",    // 接收者UID
    var text: String = "",          // 消息内容
    var timestamp: Long = 0L,       // 消息时间戳
    var type: String = "text"       // 消息类型
)
```
💡 **用途**: 存储到 Firestore `chats/{chatId}/messages/{messageId}` 子集合

**User 类** - 用户信息（可选）
```kotlin
data class User(
    var uid: String = "",           // 用户UID
    var username: String = "",      // 用户账号
    var createdAt: Long = 0L        // 创建时间
)
```
💡 **用途**: 存储到 Firestore `users/{uid}` 集合

### 2️⃣ 新增工具类 ChatUtils - 聊天工具方法

**getChatId()** - 生成聊天ID
```kotlin
fun getChatId(uid1: String, uid2: String): String
// 例子：
// getChatId("userA", "userB") → "userA_userB"
// getChatId("userB", "userA") → "userA_userB"（保证唯一性）
```

**getOtherUserId()** - 获取对方UID
```kotlin
fun getOtherUserId(chat: Chat, currentUid: String): String
// 从聊天的参与者中找到不是自己的那个UID
```

### 3️⃣ 更新 AuthManager.kt - 添加 Firestore 和新方法

#### 新增导入
```kotlin
//import com.google.firebase.firestore.FirebaseFirestore
```

#### 初始化 Firestore
```kotlin
private val db = FirebaseFirestore.getInstance()
```

#### 新增方法

**getCurrentUserUid()** - 获取当前用户UID
```kotlin
fun getCurrentUserUid(): String
// 返回 auth.currentUser?.uid ?: ""
// 用于识别聊天中的发送者和接收者
```

**getCurrentUsername()** - 获取当前用户账号
```kotlin
fun getCurrentUsername(): String
// 从邮箱中提取账号（去掉@chatapp.com）
// 例如：john@chatapp.com → john
```

**saveUserToFirestore()** - 保存用户信息到Firestore
```kotlin
fun saveUserToFirestore(username: String, onComplete: (Boolean) -> Unit)
// 在注册或登录时调用
// 写入 users/{uid} 文档
```

## 🔥 关键知识点

### Firestore 数据库结构
```
Firestore
├── users/{uid}
│   ├── username: String
│   ├── createdAt: Long
│   └── ...
├── chats/{chatId}
│   ├── participants: List<String>  ← 关键：决定谁能看到这个聊天
│   ├── lastMessage: String
│   ├── lastTimestamp: Long
│   ├── lastSenderId: String
│   └── messages/{messageId}
│       ├── senderId: String
│       ├── receiverId: String
│       ├── text: String
│       ├── timestamp: Long
│       └── type: String
```

### 为什么要存储 participants 字段？
- ✅ 使用 `whereArrayContains("participants", currentUid)` 查询当前用户的聊天
- ✅ A用户只能看到包含A的聊天
- ✅ B用户只能看到包含B的聊天
- ✅ 支持多人聊天（扩展性强）

### 为什么要分开 Chat 和 Message？
- ✅ Chat：聊天列表显示摘要信息（快速加载）
- ✅ Message：聊天详情显示所有消息（按需加载）
- ✅ 性能优化：列表不需要加载所有消息

## 📝 数据流向示例

### 场景1：A和B聊天

**初始化**
```
A发送消息给B → getChatId("uidA", "uidB") → "uidA_uidB"
```

**Firestore中的数据**
```
chats/uidA_uidB
{
    chatId: "uidA_uidB"
    participants: ["uidA", "uidB"]      ← A和B都在这里
    lastMessage: "你好"
    lastTimestamp: 1710000000000
    lastSenderId: "uidA"
}
    ├── messages/msg1
    │   {senderId: "uidA", receiverId: "uidB", text: "你好", ...}
    └── messages/msg2
        {senderId: "uidB", receiverId: "uidA", text: "你好啊", ...}
```

**查询列表**
```
A登录 → getCurrentUserUid() → "uidA"
        → whereArrayContains("participants", "uidA")
        → 找到 chats/uidA_uidB
        → 显示 "你好啊" (lastMessage)

B登录 → getCurrentUserUid() → "uidB"
        → whereArrayContains("participants", "uidB")
        → 找到 chats/uidA_uidB
        → 显示 "你好啊" (lastMessage)

C登录 → getCurrentUserUid() → "uidC"
        → whereArrayContains("participants", "uidC")
        → 找不到（C不在任何参与者列表中）
        → 显示空列表 ✅
```

## ✅ 验证

```bash
✓ Models.kt 创建成功
✓ Chat 数据类已定义
✓ Message 数据类已定义
✓ User 数据类已定义
✓ ChatUtils 工具类已定义
✓ AuthManager.kt 已更新
✓ Firestore 初始化已添加
✓ 新增方法：getCurrentUserUid()
✓ 新增方法：getCurrentUsername()
✓ 新增方法：saveUserToFirestore()
✓ Kotlin 编译成功 ✅
```

## 🎯 下一步（第二步）

修改 AuthManager 的 register 和 login 方法，在认证成功后调用 `saveUserToFirestore()` 保存用户信息到数据库。

---

**编译状态**: ✅ BUILD SUCCESSFUL
**文件修改数**: 2 个 (新增Models.kt, 修改AuthManager.kt)

