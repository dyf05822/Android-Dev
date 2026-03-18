# 📝 代码改动详细说明

## 🔧 修改的文件清单

### 1. **CloudChatManager.kt** ✅
**位置**：`app/src/main/java/com/example/screenshotoftaskmanager/CloudChatManager.kt`

**新增方法**：

#### `searchUser()` - 搜索用户
```kotlin
fun searchUser(
    username: String,
    onComplete: (User?, String) -> Unit
)
```
**功能**：
- 在 Firestore 的 users 集合中搜索指定用户名
- 防止用户搜索自己
- 返回 User 对象或错误信息

**流程**：
```
输入用户名 → 检查是否登录 → 检查是否为自己
    ↓
执行 Firestore 查询 (whereEqualTo "username")
    ↓
返回 User 对象或"未找到"错误
```

#### `createOrUpdateConversation()` - 创建或更新会话
```kotlin
fun createOrUpdateConversation(
    otherUserUid: String,
    otherUsername: String,
    onComplete: (Boolean, String) -> Unit
)
```
**功能**：
- 在 Firestore 中创建新的 Chat 文档
- 建立两个用户之间的会话关系
- 使用 `SetOptions.merge()` 防止覆盖已有数据

**流程**：
```
输入对方 UID 和用户名 → 检查登录状态
    ↓
生成稳定的 chatId (使用 ChatUtils.getChatId)
    ↓
创建 Chat 对象 (participants, lastMessage, timestamp 等)
    ↓
使用 batch.set() 保存到 Firestore
    ↓
返回成功或错误信息
```

**Firestore 操作**：
```javascript
// 创建的文档结构
{
  chatId: "uid1_uid2" // 或 "uid2_uid1"（总是排序后的结果）
  participants: [uid1, uid2]
  lastMessage: ""
  lastTimestamp: 当前时间戳
  lastSenderId: 当前用户 UID
}
```

---

### 2. **ConversationListScreen.kt** ✅
**位置**：`app/src/main/java/com/example/screenshotoftaskmanager/ui/ConversationListScreen.kt`

**新增状态变量**：
```kotlin
var showSearchDialog by remember { mutableStateOf(false) }           // 对话框显示状态
var searchInput by remember { mutableStateOf("") }                   // 搜索输入框内容
var searchStatus by remember { mutableStateOf("") }                  // 搜索状态信息
var isSearching by remember { mutableStateOf(false) }                // 搜索中标志
var foundUser by remember { mutableStateOf<User?>(null) }            // 找到的用户
```

**新增UI：搜索对话框**
```kotlin
if (showSearchDialog) {
    AlertDialog(
        title = { Text("添加好友") },
        text = {
            Column {
                OutlinedTextField(...)          // 输入框
                if (searchStatus.isNotEmpty()) { }
                Text(...)                       // 状态提示
                if (foundUser != null) { }
                Text(...)                       // 用户信息
            }
        },
        confirmButton = { ... },                 // 搜索/添加按钮
        dismissButton = { ... }                  // 取消按钮
    )
}
```

**对话框按钮逻辑**：
- **foundUser == null** (未搜索或搜索失败)
  - 按钮显示"搜索"
  - 点击调用 `CloudChatManager.searchUser()`
  
- **foundUser != null** (搜索成功)
  - 按钮显示"添加好友"
  - 点击调用 `CloudChatManager.createOrUpdateConversation()`
  - 成功后关闭对话框，消息列表自动刷新

**修改的 TopAppBar**：
```kotlin
// 原代码：显示 Toast
IconButton(onClick = {
    Toast.makeText(context, "请先让对方...", Toast.LENGTH_SHORT).show()
}) { ... }

// 新代码：打开搜索对话框
IconButton(onClick = {
    showSearchDialog = true
}) {
    Icon(Icons.Default.Add, contentDescription = "添加好友")
}
```

**新增导入**：
- `androidx.compose.material3.AlertDialog`
- `androidx.compose.material3.OutlinedTextField`
- `androidx.compose.material3.TextButton`
- `androidx.compose.runtime.mutableStateOf`
- `androidx.compose.runtime.setValue`
- `androidx.compose.runtime.getValue`

---

### 3. **ProfileScreen.kt** ✅
**位置**：`app/src/main/java/com/example/screenshotoftaskmanager/ui/ProfileScreen.kt`

**修改 TopAppBar**：
```kotlin
// 新增 actions 参数
topBar = {
    TopAppBar(
        title = { Text("我的") },
        actions = {
            // 显示当前用户ID
            Text(
                text = "ID: $currentUsername",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .align(Alignment.CenterVertically)
            )
        }
    )
}
```

**说明**：
- `currentUsername` 从 `AuthManager.getCurrentUsername()` 获取
- 显示格式：`ID: admin` 或 `ID: xiaoming`
- 位置：TopAppBar 右上角
- 颜色：白色文本
- 字体大小：14sp

**新增提示对话框**：
```kotlin
var showUploadHintDialog by remember { mutableStateOf(false) }

if (showUploadHintDialog) {
    AlertDialog(
        title = { Text("预设聊天上传说明") },
        text = { Text("请先手动注册...") },
        confirmButton = { ... }
    )
}
```

**修改上传按钮区域**：
```kotlin
// 原代码：两行纯文本提示
Text("请先手动注册...")
Text("预设消息来自...")

// 新代码：文本 + 信息图标
Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
    modifier = Modifier.fillMaxWidth()
) {
    Text("上传预设聊天到云端")
    Spacer(modifier = Modifier.width(8.dp))
    IconButton(
        onClick = { showUploadHintDialog = true },
        modifier = Modifier.size(24.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = "查看上传说明",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}
```

---

## 🔄 数据流详解

### 添加好友的完整数据流

```
用户 A (admin)                          Firestore                    用户 B (xiaoming)
    |                                       |                             |
    |-- 点击"+" 打开搜索对话框               |                             |
    |                                       |                             |
    |-- 输入 "xiaoming" 点击搜索 ----------->|                             |
    |                                       |                             |
    |                   <- 查询 users 集合
    |                       找到 xiaoming 账号
    |<-- 返回 User 对象 -----|                                             |
    |                       |                                             |
    |-- 显示用户信息，点击"添加好友" ------->|                             |
    |                                       |                             |
    |               set Chat 文档            |                             |
    |           (participants: [A_uid, B_uid])|                           |
    |                                       |                             |
    |<-- 消息列表自动刷新                    |-- 实时监听 Chat 文档 ------->|
    |  (listenMyConversations 触发)          |                             |
    |                                       |<-- xiaoming 的会话出现
    |-- 消息列表中出现 xiaoming              |
```

### 详细步骤

#### 1. 搜索阶段
```
Client A                    Firestore
  |
  |-- whereEqualTo("username", "xiaoming")
  |
  |<-- snapshot.documents
  |    [User(uid: "yyy", username: "xiaoming", ...)]
  |
  |-- 显示用户信息
```

#### 2. 创建会话阶段
```
Client A                    Firestore
  |
  |-- batch.set(Chat{
  |      chatId: "xxx_yyy",
  |      participants: ["xxx", "yyy"],
  |      lastMessage: "",
  |      lastTimestamp: now,
  |      lastSenderId: "xxx"
  |    })
  |
  |-- batch.commit()
  |
  |<-- onSuccessListener
  |    提示成功，关闭对话框
```

#### 3. 同步阶段
```
Client A                    Firestore                Client B
  |                             |                       |
  |-- listenMyConversations     |                       |
  |   (已有)                     |                       |
  |                             |<-- listenMyConversations
  |<-- snapshot changed         |   (已有)
  |    新会话出现               |
  |                             |<-- snapshot changed
  |-- 刷新消息列表              |    新会话出现
  |                             |
  |                             |-- 刷新消息列表
```

---

## 🛡️ 安全验证

### 输入验证
```kotlin
// searchUser 中的验证
if (username.isBlank()) {
    onComplete(null, "请输入有效的用户ID")
    return
}

if (username == auth.currentUser?.email?.substringBefore("@")) {
    onComplete(null, "不能添加自己为好友")
    return
}
```

### 权限检查
```kotlin
val currentUid = auth.currentUser?.uid
if (currentUid.isNullOrBlank()) {
    onComplete(null, "当前未登录")
    return
}
```

### 数据完整性
```kotlin
if (user != null && user.uid.isNotBlank()) {
    onComplete(user, "")
} else {
    onComplete(null, "未找到该用户，请检查用户ID是否正确")
}
```

---

## 🔗 关键类之间的关系

```
ConversationListScreen
    |
    |-- 点击"+" --> showSearchDialog = true
    |
    |-- OutlinedTextField(searchInput)
    |
    |-- TextButton("搜索")
         |
         |--> CloudChatManager.searchUser(searchInput)
              |
              |-- 返回 User 对象
              |
              |--> foundUser = user
    |
    |-- TextButton("添加好友")
         |
         |--> CloudChatManager.createOrUpdateConversation()
              |
              |-- 创建 Chat 文档
              |
              |--> onComplete(success, message)
                   |
                   |--> 关闭对话框
                   |--> DataSource.replaceConversations() 自动刷新
```

---

## 📦 依赖项

### 新增的 Firestore 查询
```kotlin
db.collection("users")
    .whereEqualTo("username", username)
    .get()  // 或 addSnapshotListener 实时监听
```

### 新增的 Compose 组件
- `AlertDialog` - 对话框容器
- `OutlinedTextField` - 文本输入框
- `TextButton` - 文本按钮
- `Column` - 垂直布局（用于对话框内容）

### 新增的状态管理
- `remember { mutableStateOf() }` - 记住搜索对话框状态
- `by` 委托 - 简化状态读写

---

## ✨ 特殊实现细节

### 1. 对话框状态机
```
初始状态
    |
    |-- 点击"+"
    |
显示对话框，foundUser = null
    |
    |-- 输入用户名 + 点击"搜索"
    |
搜索中 (isSearching = true)
    |
    |-- 搜索完成
    |
foundUser != null（搜索成功）或 searchStatus 有错误
    |
    |-- foundUser != null 时，按钮改为"添加好友"
    |-- foundUser == null 时，按钮仍为"搜索"
    |
    |-- 点击"添加好友"
    |
添加中 (isSearching = true)
    |
    |-- 添加完成
    |
成功：关闭对话框，消息列表自动刷新
失败：显示错误信息，保持对话框打开
```

### 2. 幂等性保证
```kotlin
// 使用 SetOptions.merge()
batch.set(chatRef, chatSummary, SetOptions.merge())

// 效果：
// - 如果 Chat 文档已存在，只更新指定字段
// - 不会覆盖现有消息
// - 重复添加同一好友不会产生重复数据
```

### 3. 稳定的 ChatID 生成
```kotlin
fun getChatId(uid1: String, uid2: String): String {
    return if (uid1 < uid2) {
        "${uid1}_${uid2}"
    } else {
        "${uid2}_${uid1}"
    }
}
// 确保 A→B 和 B→A 生成相同的 chatId
```

---

## 🚀 性能考虑

### 查询优化
```kotlin
// ✅ 使用 whereEqualTo - 高效
db.collection("users")
    .whereEqualTo("username", "xiaoming")

// ❌ 避免全表扫描
db.collection("users").get()
```

### 实时监听
```kotlin
// ✅ 已有的监听机制
CloudChatManager.listenMyConversations()
// 自动监听新会话，无需手动刷新

// ✅ 使用 SetOptions.merge()
// 避免不必要的覆盖写入
```

---

## 📚 相关文件

| 文件 | 说明 |
|------|------|
| `CloudChatManager.kt` | 核心逻辑 |
| `ConversationListScreen.kt` | 搜索UI |
| `ProfileScreen.kt` | ID显示 |
| `Models.kt` | User/Chat 数据类 |
| `AuthManager.kt` | 认证管理 |
| `DataSource.kt` | 本地缓存 |

---

## 🔍 调试技巧

### 查看日志
```
// 搜索用户时的日志
D/CloudChatManager: searchUser: username=xiaoming
D/CloudChatManager: 查询成功，返回User对象
D/CloudChatManager: 用户ID: yyy

// 创建会话时的日志
D/CloudChatManager: createOrUpdateConversation: uid1=xxx, uid2=yyy
D/CloudChatManager: chatId=xxx_yyy
D/CloudChatManager: Chat文档创建成功
```

### 查看 Firestore 数据
1. 打开 Firebase Console
2. 进入 Firestore Database
3. 查看 `users` 和 `chats` 集合
4. 验证文档结构和数据

### 模拟网络延迟
- 在 CloudChatManager 中添加 Thread.sleep()
- 测试 UI 的加载状态是否正确显示


