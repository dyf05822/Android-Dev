package com.example.screenshotoftaskmanager // 声明包名，确保文件属于正确的命名空间

import com.google.firebase.auth.FirebaseAuth // 导入 Firebase 身份验证库，用于用户注册和登录功能

// 创建一个单例对象，全局只有一个 AuthManager 实例，用于管理所有身份验证操作
object AuthManager {

    // 初始化 FirebaseAuth 实例，用于处理用户身份验证相关的操作
    private val auth = FirebaseAuth.getInstance()

    // 将用户输入的账号转换为邮箱格式，方便 Firebase 认证（Firebase 使用邮箱作为标识符）
    private fun convertUsernameToEmail(username: String): String {
        // 将账号转换为 username@chatapp.com 的邮箱格式
        return "$username@chatapp.com"
    }

    // 注册函数：接收账号、密码和回调函数，用于创建新用户账户
    fun register(username: String, password: String, callback: (Boolean, String) -> Unit) {
        // 将用户输入的账号转换为邮箱格式
        val email = convertUsernameToEmail(username)
        
        // 调用 Firebase 的用户创建方法，异步创建新账户
        auth.createUserWithEmailAndPassword(email, password)
            // 添加监听器，当操作完成时执行此代码块
            .addOnCompleteListener { task ->
                // 判断操作是否成功
                if (task.isSuccessful) {
                    // 如果注册成功，调用回调函数返回 true 和成功消息
                    callback(true, "注册成功 ✅ 账户已创建，请登录")
                } else {
                    // 如果注册失败，调用回调函数返回 false 和错误信息
                    // 如果异常信息为空，则显示通用错误消息
                    callback(false, task.exception?.message ?: "注册失败 ❌ 请检查账号和密码")
                }
            }
    }

    // 登录函数：接收账号、密码和回调函数，用于用户登录
    fun login(username: String, password: String, callback: (Boolean, String) -> Unit) {
        // 将用户输入的账号转换为邮箱格式
        val email = convertUsernameToEmail(username)
        
        // 调用 Firebase 的用户登录方法，异步登录现有账户
        auth.signInWithEmailAndPassword(email, password)
            // 添加监听器，当操作完成时执行此代码块
            .addOnCompleteListener { task ->
                // 判断操作是否成功
                if (task.isSuccessful) {
                    // 如果登录成功，调用回调函数返回 true 和成功消息
                    callback(true, "登录成功 ✅ 欢迎回来")
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
}

