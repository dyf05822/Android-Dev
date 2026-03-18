package com.example.screenshotoftaskmanager // 声明包名，确保文件属于正确的命名空间

import com.google.firebase.auth.FirebaseAuth // 导入 Firebase 身份验证库，用于用户注册和登录功能
import com.google.firebase.firestore.FirebaseFirestore // 导入 Firestore 数据库库，用于存储聊天数据和用户信息

// 创建一个单例对象，全局只有一个 AuthManager 实例，用于管理所有身份验证操作
object AuthManager {

    // 初始化 FirebaseAuth 实例，用于处理用户身份验证相关的操作
    private val auth = FirebaseAuth.getInstance()
    
    // 初始化 Firestore 实例，用于存储和读取聊天、用户信息等数据
    private val db = FirebaseFirestore.getInstance()

    // 将用户输入的账号转换为邮箱格式，方便 Firebase 认证（Firebase 使用邮箱作为标识符）
    private fun convertUsernameToEmail(username: String): String {
        // 先去掉账号首尾的空格，避免用户误输入空格导致登录失败
        val normalizedUsername = username.trim()
        // 将账号转换为 username@chatapp.com 的邮箱格式
        return "$normalizedUsername@chatapp.com"
    }

    // 注册函数：接收账号、密码和回调函数，用于创建新用户账户
    fun register(username: String, password: String, callback: (Boolean, String) -> Unit) {
        // 先规范化账号文本，避免把首尾空格一起提交给 Firebase
        val normalizedUsername = username.trim()
        // 将用户输入的账号转换为邮箱格式
        val email = convertUsernameToEmail(normalizedUsername)
        
        // 调用 Firebase 的用户创建方法，异步创建新账户
        auth.createUserWithEmailAndPassword(email, password)
            // 添加监听器，当操作完成时执行此代码块
            .addOnCompleteListener { task ->
                // 判断操作是否成功
                if (task.isSuccessful) {
                    // 注册成功后，立即把用户资料写入 Firestore，确保后续能通过 username 找到 uid
                    saveUserToFirestore(normalizedUsername) { saveSuccess ->
                        // 为了避免注册页返回登录页时仍保留登录态，这里主动退出当前新注册账号
                        auth.signOut()

                        // 根据资料同步结果返回更明确的提示
                        if (saveSuccess) {
                            callback(true, "注册成功 ✅ 账户已创建，请登录")
                        } else {
                            callback(false, "注册成功，但用户资料写入失败 ❌ 请检查 Firestore 权限")
                        }
                    }
                } else {
                    // 如果注册失败，调用回调函数返回 false 和错误信息
                    // 如果异常信息为空，则显示通用错误消息
                    callback(false, task.exception?.message ?: "注册失败 ❌ 请检查账号和密码")
                }
            }
    }

    // 登录函数：接收账号、密码和回调函数，用于用户登录
    fun login(username: String, password: String, callback: (Boolean, String) -> Unit) {
        // 先规范化账号文本，避免把首尾空格一起提交给 Firebase
        val normalizedUsername = username.trim()
        // 将用户输入的账号转换为邮箱格式
        val email = convertUsernameToEmail(normalizedUsername)
        
        // 调用 Firebase 的用户登录方法，异步登录现有账户
        auth.signInWithEmailAndPassword(email, password)
            // 添加监听器，当操作完成时执行此代码块
            .addOnCompleteListener { task ->
                // 判断操作是否成功
                if (task.isSuccessful) {
                    // 登录成功后先立刻回调 UI，避免 Firestore 同步慢时把登录页一直卡在灰色加载状态
                    callback(true, "登录成功 ✅ 欢迎回来")
                    // 在后台补写一次 users 文档，防止老账号没有用户资料导致云端聊天无法匹配用户名
                    saveUserToFirestore(normalizedUsername) { _ ->
                        // 这里故意不再阻塞登录流程；即使资料同步失败，也不影响用户先进入 App
                    }
                } else {
                    // 如果登录失败，调用回调函数返回 false 和错误信息
                    // 如果异常信息为空，则显示通用错误消息
                    callback(false, task.exception?.message ?: "登录失败 ❌ 账号或密码错误")
                }
            }
    }

    // 获取当前登录用户的邮箱地址，如果没有登录则返回空字符串
    fun getCurrentUserEmail(): String {
        // 获取当前认证的用户，如果为空返回 null
        return auth.currentUser?.email ?: ""
    }

    // 检查用户是否已经登录
    fun isUserLoggedIn(): Boolean {
        // 如果当前用户不为空，说明用户已登录，返回 true；否则返回 false
        return auth.currentUser != null
    }

    // 登出函数：将用户从 Firebase 注销，清除登录状态
    fun logout() {
        // 调用 Firebase 的登出方法，清除当前用户的登录状态
        auth.signOut()
    }

    // ================== 新增：聊天相关方法 ==================
    
    /**
     * 获取当前登录用户的 UID
     * UID 是 Firebase 分配给每个用户的唯一标识符，用于识别聊天中的发送者和接收者
     * 
     * @return 当前用户的 UID，如果未登录则返回空字符串
     */
    fun getCurrentUserUid(): String {
        // 获取当前认证的用户，如果登录了返回其 UID，否则返回空字符串
        return auth.currentUser?.uid ?: ""
    }

    /**
     * 获取当前登录用户的账号（邮箱格式）
     * 这个方法从邮箱中提取原始账号（去掉 @chatapp.com 后缀）
     * 
     * @return 当前用户的账号，如果未登录则返回空字符串
     *
     * 示例：
     * 如果用户邮箱是 "john@chatapp.com"，则返回 "john"
     */
    fun getCurrentUsername(): String {
        // 获取当前用户的邮箱
        val email = auth.currentUser?.email ?: ""
        // 从邮箱中提取账号（去掉 @chatapp.com 部分）
        return email.substringBefore("@")
    }

    /**
     * 将用户信息保存到 Firestore
     * 在用户注册或登录时调用，存储用户的基本信息到 users 集合
     * 
     * @param username 用户的账号/用户名
     * @param onComplete 操作完成时的回调（成功或失败）
     */
    fun saveUserToFirestore(username: String, onComplete: (Boolean) -> Unit) {
        // 获取当前登录用户的 UID
        val uid = auth.currentUser?.uid
        
        // 如果用户未登录，返回失败
        if (uid == null) {
            onComplete(false)
            return
        }

        // 创建用户信息对象，用于存储到 Firestore
        val user = User(
            uid = uid, // 用户的唯一标识符
            username = username, // 用户输入的账号
            createdAt = System.currentTimeMillis() // 创建时间（当前时间戳）
        )

        // 将用户信息写入 Firestore 的 users 集合中
        // 路径：users/{uid}
        db.collection("users") // 访问 users 集合
            .document(uid) // 使用用户 UID 作为文档 ID
            .set(user) // 保存用户信息
            .addOnSuccessListener { // 成功时执行
                onComplete(true) // 调用回调函数，返回成功
            }
            .addOnFailureListener { // 失败时执行
                onComplete(false) // 调用回调函数，返回失败
            }
    }
}
