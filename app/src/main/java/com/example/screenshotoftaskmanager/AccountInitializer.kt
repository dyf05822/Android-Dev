package com.example.screenshotoftaskmanager // 声明包名

import android.os.Handler // 导入 Handler，用于给异步认证操作增加超时保护
import android.os.Looper // 导入 Looper，用于把超时任务投递到主线程消息队列
import android.util.Log // 导入日志库，用于调试
import com.google.firebase.FirebaseApp // 导入 FirebaseApp，用于创建二级 Firebase 实例
import com.google.firebase.auth.FirebaseAuth // 导入Firebase认证库
import com.google.firebase.auth.FirebaseAuthUserCollisionException // 导入账号冲突异常类型，用于更稳定地判断“账号已存在”
import com.google.firebase.firestore.FirebaseFirestore // 导入Firestore数据库库
import java.util.concurrent.atomic.AtomicBoolean // 导入原子布尔值，用于确保成功/失败/超时只处理一次

/**
 * 账号初始化工具
 * 用于创建系统账号（xiaoming、xiaohua、daguang）和初始化聊天数据
 * 这个类应该在 admin 账号登录后调用一次
 */
object AccountInitializer {
    
    // 初始化Firebase认证实例
    private val auth = FirebaseAuth.getInstance()

    // 使用只读属性动态获取 Firestore，避免持有静态 Context 引用
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()    //这里get是只读属性每次访问时动态拿实例笔名啊不必要的静态使用

    // 二级 FirebaseApp 的名称，用于隔离创建账号流程，避免挤掉当前 admin 登录态  注意这里很重要！！！！
    private const val SECONDARY_APP_NAME = "account_initializer_secondary"  //二级 FirebaseApp 的名字

    // 认证请求超时时间（毫秒）
    private const val AUTH_TIMEOUT_MS = 20000L   //人话：20秒
    
    // 定义系统账号的信息（账号、密码、显示名称） 初始化脚本的数据源
    private val systemAccounts = listOf(
        // 系统账号1：小明
        mapOf(                                 //mapof是创建只读键值对集合的函数
            "username" to "xiaoming", // 账号
            "password" to "123456", // 密码
            "displayName" to "小明" // 显示名称
        ),
        // 系统账号2：小华
        mapOf(
            "username" to "xiaohua", // 账号
            "password" to "123456", // 密码
            "displayName" to "小华" // 显示名称
        ),
        // 系统账号3：大光
        mapOf(
            "username" to "daguang", // 账号
            "password" to "123456", // 密码
            "displayName" to "大光" // 显示名称
        )
    )

    /**
     * 管理员一键初始化入口：创建系统账号 + 初始化聊天数据
     * 只允许当前登录账号为 admin 时执行
     */
    fun bootstrapSystemDataForAdmin(        //为管理员加载系统数据的入口函数
        onProgress: (String) -> Unit = { _ -> }, // 进度文本回调
        onComplete: (Boolean, String) -> Unit // 完成回调（成功标记 + 文本）
    ) {
        // 获取当前登录用户
        val currentUser = auth.currentUser

        // 如果当前未登录，直接失败
        if (currentUser == null) {
            onComplete(false, "请先登录 admin 账号")
            return
        }

        // 从邮箱中提取当前账号名（项目里账号被转换成 username@chatapp.com）
        val currentUsername = currentUser.email?.substringBefore("@") ?: ""

        // 如果不是 admin，拒绝执行
        if (currentUsername != "admin") {
            onComplete(false, "只有 admin 账号可以执行初始化")
            return
        }

        // 记录 admin 的 UID，后续初始化聊天需要它
        val adminUid = currentUser.uid

        // 先创建系统账号
        createSystemAccounts(
            onProgress = { message, _, progress ->    //过程回调  可能会被调用很多次
                onProgress("[账号初始化 $progress] $message") // 透传并补充阶段信息
            },
            onComplete = { accountPhaseSuccess ->    //账户阶段成功   结果回调 只调用一次
                // 再初始化聊天记录
                initializeChatMessages(
                    adminUid = adminUid,
                    onProgress = { message, _ ->
                        onProgress("[聊天初始化] $message") // 透传聊天初始化进度
                    },
                    onComplete = { chatPhaseSuccess, finalMessage ->
                        // 两个阶段都成功才算整体成功
                        val finalSuccess = accountPhaseSuccess && chatPhaseSuccess
                        onComplete(finalSuccess, finalMessage)   //结果回调
                    }
                )
            }
        )
    }

    /**
     * 获取或创建二级 Auth 实例
     * 使用二级实例创建账号，不会改变主实例当前登录用户
     */
    private fun getOrCreateSecondaryAuth(): FirebaseAuth {
        // 获取默认 FirebaseApp
        val defaultApp = FirebaseApp.getInstance()

        // 获取应用 Context
        val appContext = defaultApp.applicationContext

        // 先查找是否已存在同名二级 FirebaseApp
        val existingApp = FirebaseApp.getApps(appContext).firstOrNull { it.name == SECONDARY_APP_NAME }

        // 如果不存在则创建一个新的二级 FirebaseApp
        val secondaryApp = existingApp ?: FirebaseApp.initializeApp(appContext, defaultApp.options, SECONDARY_APP_NAME)

        // 返回二级 FirebaseApp 对应的 Auth 实例
        return FirebaseAuth.getInstance(secondaryApp)
    }

    /**
     * 为“已存在账号”补写 users/{uid} 文档，避免后续无法根据 username 找到 UID
     */
    private fun ensureExistingAccountUserDocument(   //补写已存在账号的 users 文档
        secondaryAuth: FirebaseAuth, // 二级认证实例
        username: String, // 账号
        password: String, // 密码
        onComplete: (Boolean) -> Unit // 完成回调
    ) {
        // 拼接 Firebase Auth 需要的邮箱格式
        val email = "${username}@chatapp.com"

        // 先退出二级认证当前账号，避免残留登录态影响本次补写流程 避免上一次残留的登录态污染本次流程
        secondaryAuth.signOut()

        // 创建一个原子标记，保证登录成功/失败/超时只会处理一次   否则可能被回调两次
        val callbackHandled = AtomicBoolean(false)

        // 创建一个主线程 Handler，用于启动超时计时器
        val timeoutHandler = Handler(Looper.getMainLooper())

        // 定义超时后的兜底逻辑，防止 Firebase Auth 一直 pending 导致界面卡住
        val timeoutRunnable = Runnable {
            // 只有第一次进入时才真正执行超时逻辑
            if (callbackHandled.compareAndSet(false, true)) {
                // 打印更明确的超时日志（log），方便在 Logcat 中定位问题
                Log.e("AccountInitializer", "补写已存在账号资料超时: $username")

                // 清理二级认证状态，避免后续流程被脏状态污染
                secondaryAuth.signOut()

                // 回调失败，让外层继续往下处理，而不是一直卡死
                onComplete(false)
            }
        }

        // 启动超时计时器
        timeoutHandler.postDelayed(timeoutRunnable, AUTH_TIMEOUT_MS)   //延迟执行

        // 用二级 Auth 登录这个已存在账号，以拿到 UID
        secondaryAuth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { signInResult ->
                // 如果已经被超时或其他回调处理过，就忽略这次迟到回调
                if (!callbackHandled.compareAndSet(false, true)) {
                    return@addOnSuccessListener
                }

                // 登录成功后先取消超时任务
                timeoutHandler.removeCallbacks(timeoutRunnable)

                // 读取该账号 UID
                val uid = signInResult.user?.uid ?: ""

                // 如果 UID 为空，直接失败
                if (uid.isBlank()) {
                    secondaryAuth.signOut()
                    onComplete(false)
                    return@addOnSuccessListener    //带标签返回  跳出lambda
                }

                // 组装 users 文档
                val user = User(
                    uid = uid,
                    username = username,
                    createdAt = System.currentTimeMillis()
                )

                // 写入 users/{uid}
                db.collection("users")
                    .document(uid)
                    .set(user)
                    .addOnSuccessListener {
                        // 补写成功后登出二级账号，避免污染状态
                        secondaryAuth.signOut()

                        onComplete(true)
                    }
                    .addOnFailureListener {    //添加失败监听器
                        // 补写失败后同样登出
                        secondaryAuth.signOut()

                        onComplete(false)   //完成事件 回调函数
                    }
            }
            .addOnFailureListener {
                // 如果已经被超时或其他回调处理过，就忽略这次迟到回调
                if (!callbackHandled.compareAndSet(false, true)) {
                    return@addOnFailureListener
                }

                // 登录失败后先取消超时任务
                timeoutHandler.removeCallbacks(timeoutRunnable)

                // 登录已存在账号失败，无法补写文档
                secondaryAuth.signOut()

                onComplete(false)
            }
    }
    
    /**
     * 创建系统账号
     * 这个方法会逐个创建这3个系统账号
     */
    fun createSystemAccounts(     //创建系统账号
        onProgress: (String, Boolean, String) -> Unit, // 定义回调函数类型
        onComplete: (Boolean) -> Unit = { _ -> } // 阶段完成回调，true 表示本阶段整体成功
    ) {
        // 检查当前是否有用户登录
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // 如果没有用户登录，返回错误提示
            onProgress("错误：请先登录admin账号", false, "0/3")
            onComplete(false)
            return
        }

        // 获取二级 Auth，用于无感创建账号
        val secondaryAuth = getOrCreateSecondaryAuth()

        // 开始创建账号，更新进度为开始状态
        onProgress("开始创建系统账号...", true, "0/${systemAccounts.size}")

        // 使用递归方式逐个创建账号
        createAccountRecursively(
            index = 0,
            secondaryAuth = secondaryAuth,
            hasFailure = false,
            onProgress = onProgress,
            onComplete = onComplete
        )
    }

    /**
     * 递归创建账号的私有方法
     */
    private fun createAccountRecursively(    //创建递归账户
        index: Int, // 当前处理的账号索引
        secondaryAuth: FirebaseAuth, // 二级认证实例（不会影响主登录态）
        hasFailure: Boolean, // 到当前为止是否出现过失败
        onProgress: (String, Boolean, String) -> Unit, // 进度回调
        onComplete: (Boolean) -> Unit // 阶段完成回调
    ) {
        // 检查是否已经创建完所有账号
        if (index >= systemAccounts.size) {
            // 所有账号都创建完后，登出二级认证实例
            secondaryAuth.signOut()

            // 所有账号都创建完了
            onProgress("✅ 所有系统账号创建完成！", !hasFailure, "${systemAccounts.size}/${systemAccounts.size}")

            // 返回阶段最终结果
            onComplete(!hasFailure)
            return
        }

        // 获取当前要创建的账号信息
        val account = systemAccounts[index] // 获取第index个账号的信息
        val username = account["username"] as String // 提取账号名
        val password = account["password"] as String // 提取密码
        val displayName = account["displayName"] as String // 提取显示名称

        // 将账号转换为邮箱格式（Firebase要求邮箱格式）
        val email = "${username}@chatapp.com" // 转换为 username@chatapp.com

        // 开始创建这个账号
        onProgress("正在创建账号：$displayName ($username)...", true, "${index}/${systemAccounts.size}")

        // 打印开始创建日志，便于确认卡在请求发出前还是发出后
        Log.d("AccountInitializer", "开始请求创建账号: $username")

        // 先退出二级认证当前账号，避免前一次创建后残留登录态影响后续请求
        secondaryAuth.signOut()

        // 创建一个原子标记，确保成功/失败/超时只会被处理一次
        val callbackHandled = AtomicBoolean(false)

        // 创建一个主线程 Handler，用于给 Firebase Auth 请求加超时保护
        val timeoutHandler = Handler(Looper.getMainLooper())

        // 定义超时逻辑：如果超过设定时间还没有回调，就当作失败继续往下走
        val timeoutRunnable = Runnable {
            // 只有第一次触发时才执行超时逻辑，防止和成功/失败回调重复执行
            if (callbackHandled.compareAndSet(false, true)) {
                // 打印超时日志，方便用户从 Logcat 判断是网络/Firebase pending 问题
                Log.e("AccountInitializer", "创建账号超时: $username")

                // 更新界面进度，让用户知道不是程序死掉，而是认证请求超时
                onProgress("❌ 创建超时：$displayName（请检查网络 / Firebase 连接）", false, "${index + 1}/${systemAccounts.size}")

                // 继续处理下一个账号，避免整个初始化流程永远卡在第一个账号  执行下一个帐号了
                createAccountRecursively(
                    index = index + 1,
                    secondaryAuth = secondaryAuth,
                    hasFailure = true,
                    onProgress = onProgress,
                    onComplete = onComplete
                )
            }
        }

        // 启动超时计时器
        timeoutHandler.postDelayed(timeoutRunnable, AUTH_TIMEOUT_MS)

        // 调用二级 Auth 的创建用户方法（不会切换主账号登录状态）
        secondaryAuth.createUserWithEmailAndPassword(email, password)
            // 添加成功监听器
            .addOnSuccessListener { authResult ->
                // 如果已经被超时或其他回调处理过，就忽略这次迟到回调
                if (!callbackHandled.compareAndSet(false, true)) {
                    return@addOnSuccessListener
                }

                // 成功回调到达后，先取消超时任务
                timeoutHandler.removeCallbacks(timeoutRunnable)

                // 账号创建成功，获取新创建用户的UID
                val uid = authResult.user?.uid ?: "" // 获取用户UID

                // 打印日志（便于调试）
                Log.d("AccountInitializer", "✅ 账号创建成功: $username (UID: $uid)") // 打印成功日志

                // 将用户信息保存到Firestore的users集合中
                val user = User(
                    uid = uid, // 用户的唯一标识符
                    username = username, // 用户账号
                    createdAt = System.currentTimeMillis() // 创建时间（当前时间戳）
                )

                // 写入users集合
                db.collection("users") // 访问users集合
                    .document(uid) // 使用UID作为文档ID
                    .set(user) // 保存用户信息
                    .addOnSuccessListener {
                        // 用户信息保存成功
                        Log.d("AccountInitializer", "用户信息已保存到Firestore: $username") // 打印日志

                        // 回调当前账号创建成功进度
                        onProgress("✅ 账号创建成功：$displayName", true, "${index + 1}/${systemAccounts.size}")

                        // 继续创建下一个账号
                        createAccountRecursively(
                            index = index + 1,
                            secondaryAuth = secondaryAuth,
                            hasFailure = hasFailure,
                            onProgress = onProgress,
                            onComplete = onComplete
                        )
                    }
                    .addOnFailureListener { e ->
                        // 保存用户信息失败
                        Log.e("AccountInitializer", "保存用户信息失败: ${e.message}") // 打印错误日志

                        // 回调失败信息
                        onProgress("❌ 保存用户资料失败：$displayName", false, "${index + 1}/${systemAccounts.size}")

                        // 继续创建下一个账号（即使这个失败了）
                        createAccountRecursively(
                            index = index + 1,
                            secondaryAuth = secondaryAuth,
                            hasFailure = true,
                            onProgress = onProgress,
                            onComplete = onComplete
                        )
                    }
            }
            // 添加失败监听器
            .addOnFailureListener { e ->
                // 如果已经被超时或其他回调处理过，就忽略这次迟到回调
                if (!callbackHandled.compareAndSet(false, true)) {
                    return@addOnFailureListener
                }

                // 失败回调到达后，先取消超时任务
                timeoutHandler.removeCallbacks(timeoutRunnable)

                // 账号创建失败，可能是账号已存在
                val errorMessage = e.message ?: "未知错误" // 获取错误信息

                // 打印日志
                Log.w("AccountInitializer", "账号创建失败 ($username): $errorMessage") // 打印警告日志

                // 如果是"账号已存在"的错误，则尝试补写 users 文档再继续
                if (e is FirebaseAuthUserCollisionException) {
                    // 尝试登录已存在账号并补写 users 文档
                    ensureExistingAccountUserDocument(secondaryAuth, username, password) { ensured ->
                        // 根据补写结果更新提示文本
                        if (ensured) {
                            onProgress("⚠️ 账号已存在：$displayName（已校准用户资料）", true, "${index + 1}/${systemAccounts.size}")
                        } else {
                            onProgress("⚠️ 账号已存在：$displayName（跳过，资料校准失败）", false, "${index + 1}/${systemAccounts.size}")
                        }

                        // 继续创建下一个账号
                        createAccountRecursively(
                            index = index + 1,
                            secondaryAuth = secondaryAuth,
                            hasFailure = hasFailure || !ensured,
                            onProgress = onProgress,
                            onComplete = onComplete
                        )
                    }
                } else {
                    // 其他错误，更新进度并继续
                    onProgress("❌ 创建失败：$displayName ($errorMessage)", false, "${index + 1}/${systemAccounts.size}")

                    // 继续创建下一个账号
                    createAccountRecursively(
                        index = index + 1,
                        secondaryAuth = secondaryAuth,
                        hasFailure = true,
                        onProgress = onProgress,
                        onComplete = onComplete
                    )
                }
            }
    }

    /**
     * 初始化聊天记录
     * 在系统账号创建完成后调用此方法，创建admin与这些系统账号之间的聊天记录
     * 这样admin登录后就能看到这些聊天
     */
    fun initializeChatMessages(
        adminUid: String, // admin账号的UID
        onProgress: (String, Boolean) -> Unit = { _, _ -> }, // 可选的进度回调
        onComplete: (Boolean, String) -> Unit = { _, _ -> } // 阶段完成回调
    ) {
        // 获取所有用户，用来找出三个系统账号的UID
        db.collection("users")
            .get()
            .addOnSuccessListener { snapshot ->
                // 查询成功，遍历所有用户
                val userMap = mutableMapOf<String, String>() // 用来存储 username -> uid 的映射

                // 遍历所有用户文档
                for (doc in snapshot.documents) {     //snapshot 是get（）成功返回的querysnapshot 这些是firebase sdk带的字段
                    // 从文档中提取user对象
                    val user = doc.toObject(User::class.java)
                    if (user != null) {
                        // 将 username -> uid 存入map   映射 （键值对集合）
                        userMap[user.username] = user.uid // 建立用户名和UID的对应关系
                    }
                }

                // 获取三个系统账号的UID
                val xiaomingUid = userMap["xiaoming"]
                val xiaohuaUid = userMap["xiaohua"]
                val daguangUid = userMap["daguang"]

                // 如果任意系统账号 UID 缺失，则直接失败
                if (xiaomingUid.isNullOrBlank() || xiaohuaUid.isNullOrBlank() || daguangUid.isNullOrBlank()) {
                    onProgress("❌ 系统账号UID不完整，请先完成账号初始化", false)
                    onComplete(false, "聊天初始化失败：系统账号信息不完整")
                    return@addOnSuccessListener
                }

                // 打印日志（调试用）
                Log.d("AccountInitializer", "开始初始化聊天记录") // 打印开始日志
                Log.d("AccountInitializer", "Admin UID: $adminUid") // 打印admin的UID
                Log.d("AccountInitializer", "小明 UID: $xiaomingUid") // 打印小明的UID

                // 统一基准时间，保证一组初始消息时间有序且可读
                val baseTime = System.currentTimeMillis()

                // 记录异步任务总数（3个会话）
                var pendingCount = 3

                // 记录成功与失败数量
                var successCount = 0
                var failureCount = 0

                // 封装一个统一的完成统计函数，避免重复代码
                fun finishOne(success: Boolean, successText: String, failText: String) {
                    if (success) {
                        successCount += 1
                        onProgress(successText, true)
                    } else {
                        failureCount += 1
                        onProgress(failText, false)
                    }

                    pendingCount -= 1

                    if (pendingCount == 0) {
                        val allSuccess = failureCount == 0
                        val finalMessage = if (allSuccess) {   //全部成功
                            "✅ 聊天初始化完成（$successCount/3）"
                        } else {
                            "⚠️ 聊天初始化完成（成功$successCount，失败$failureCount）"
                        }
                        onComplete(allSuccess, finalMessage)
                    }
                }

                // 创建admin与小明的聊天记录
                createChatWithInitialMessages(adminUid, xiaomingUid, "小明", listOf(
                    // 第一条消息：小明发送
                    Message(
                        senderId = xiaomingUid, // 发送者是小明
                        receiverId = adminUid, // 接收者是admin
                        text = "明天干饭去", // 消息内容
                        timestamp = baseTime - 300000, // 时间戳（5分钟前） 好尼玛时间戳都是随便写的啊卧槽
                        type = "text" // 消息类型
                    ),
                    // 第二条消息：admin发送
                    Message(
                        senderId = adminUid, // 发送者是admin
                        receiverId = xiaomingUid, // 接收者是小明
                        text = "好啊，去哪吃？", // 消息内容
                        timestamp = baseTime - 240000, // 时间戳（4分钟前）
                        type = "text" // 消息类型
                    ),
                    // 第三条消息：小明发送
                    Message(
                        senderId = xiaomingUid,
                        receiverId = adminUid,
                        text = "这是那家新开的串串店",
                        timestamp = baseTime - 180000,
                        type = "text"
                    ),
                    // 第四条消息：小明发送
                    Message(
                        senderId = xiaomingUid,
                        receiverId = adminUid,
                        text = "位置我发你，明天直接在那碰头？",
                        timestamp = baseTime - 120000,
                        type = "text"
                    )
                )) { success ->
                    finishOne(success, "✅ 小明的聊天记录已初始化", "❌ 小明的聊天记录初始化失败")
                }

                // 创建admin与小华的聊天记录
                createChatWithInitialMessages(adminUid, xiaohuaUid, "小华", listOf(
                    // 第一条消息：小华发送
                    Message(
                        senderId = xiaohuaUid,
                        receiverId = adminUid,
                        text = "周末有空吗？一起打球？",
                        timestamp = baseTime - 200000,
                        type = "text"
                    ),
                    // 第二条消息：admin发送
                    Message(
                        senderId = adminUid,
                        receiverId = xiaohuaUid,
                        text = "[图片]",
                        timestamp = baseTime - 100000,
                        type = "text"
                    )
                )) { success ->
                    finishOne(success, "✅ 小华的聊天记录已初始化", "❌ 小华的聊天记录初始化失败")
                }

                // 创建admin与大光的聊天记录
                createChatWithInitialMessages(adminUid, daguangUid, "大光", listOf(
                    // 第一条消息：大光发送
                    Message(
                        senderId = daguangUid,
                        receiverId = adminUid,
                        text = "在干嘛呢？",
                        timestamp = baseTime - 150000,
                        type = "text"
                    ),
                    // 第二条消息：admin发送
                    Message(
                        senderId = adminUid,
                        receiverId = daguangUid,
                        text = "刚吃完饭，准备休息一下。",
                        timestamp = baseTime - 100000,
                        type = "text"
                    ),
                    // 第三条消息：大光发送
                    Message(
                        senderId = daguangUid,
                        receiverId = adminUid,
                        text = "你现在到哪了？",
                        timestamp = baseTime - 50000,
                        type = "text"
                    )
                )) { success ->
                    finishOne(success, "✅ 大光的聊天记录已初始化", "❌ 大光的聊天记录初始化失败")
                }
            }
            .addOnFailureListener { e ->
                // 查询用户失败
                Log.e("AccountInitializer", "查询用户失败: ${e.message}") // 打印错误日志
                onProgress("❌ 查询用户失败: ${e.message}", false) // 回调错误信息
                onComplete(false, "聊天初始化失败：查询用户失败")
            }
    }

    /**
     * 创建聊天及其初始消息的私有方法
     */
    private fun createChatWithInitialMessages(
        uid1: String, // admin的UID
        uid2: String, // 系统账号的UID
        otherUsername: String, // 对方的显示名称
        messages: List<Message>, // 初始消息列表
        onComplete: (Boolean) -> Unit // 完成时的回调
    ) {
        // 生成聊天ID（确保唯一性）
        val chatId = ChatUtils.getChatId(uid1, uid2) // 使用工具方法生成chatId

        // 创建Chat对象
        val chat = Chat(
            chatId = chatId, // 聊天ID
            participants = listOf(uid1, uid2), // 参与者为admin和系统账号
            lastMessage = messages.lastOrNull()?.text ?: "", // 最后一条消息
            lastTimestamp = messages.lastOrNull()?.timestamp ?: System.currentTimeMillis(), // 最后消息时间
            lastSenderId = messages.lastOrNull()?.senderId ?: uid1 // 最后消息发送者
        )

        // 先查询 chat 文档是否已存在，存在则跳过，保证幂等
        db.collection("chats")
            .document(chatId)
            .get()
            .addOnSuccessListener { existingDoc ->
                // 如果聊天已存在，直接返回成功，不重复写入消息
                if (existingDoc.exists()) {
                    Log.d("AccountInitializer", "Chat已存在，跳过初始化: $chatId")
                    onComplete(true)
                    return@addOnSuccessListener
                }

                // Chat 不存在时再创建文档
                db.collection("chats")
                    .document(chatId)
                    .set(chat)
                    .addOnSuccessListener {
                        // Chat文档创建成功，现在添加消息
                        Log.d("AccountInitializer", "Chat文档已创建: $chatId")

                        // 添加所有消息到messages子集合
                        addMessagesToChat(chatId, messages, onComplete)
                    }
                    .addOnFailureListener { e ->
                        // Chat文档创建失败
                        Log.e("AccountInitializer", "Chat文档创建失败 ($otherUsername): ${e.message}")
                        onComplete(false)
                    }
            }
            .addOnFailureListener { e ->
                // 查询 chat 是否存在失败
                Log.e("AccountInitializer", "查询Chat失败 ($otherUsername): ${e.message}")
                onComplete(false)
            }
    }

    /**
     * 将消息添加到聊天的私有方法
     */
    private fun addMessagesToChat(
        chatId: String, // 聊天ID
        messages: List<Message>, // 消息列表
        onComplete: (Boolean) -> Unit // 完成时的回调
    ) {
        // 使用批量写入操作，提高效率
        val batch = db.batch() // 创建批量写入对象

        // 遍历所有消息，添加到批量操作中
        messages.forEach { message ->
            // 为每条消息生成可重复计算的文档ID，避免重复初始化时产生重复消息
            val stableMessageId = "msg_${message.timestamp}_${message.senderId.takeLast(6)}"

            // 为当前消息定位文档引用
            val messageRef = db.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(stableMessageId)

            // 将这条消息添加到批量操作中
            batch.set(messageRef, message)
        }

        // 提交批量操作
        batch.commit() // 执行所有写入操作
            .addOnSuccessListener {
                // 所有消息都已添加成功
                Log.d("AccountInitializer", "消息已添加到聊天 ($chatId)") // 打印日志
                onComplete(true) // 回调成功
            }
            .addOnFailureListener { e ->
                // 消息添加失败
                Log.e("AccountInitializer", "消息添加失败 ($chatId): ${e.message}") // 打印错误日志
                onComplete(false) // 回调失败
            }
    }
}
