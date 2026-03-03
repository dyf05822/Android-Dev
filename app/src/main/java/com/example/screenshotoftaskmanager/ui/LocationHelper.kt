package com.example.screenshotoftaskmanager.ui // 定义包名

import android.annotation.SuppressLint // 导入忽略警告注解
import android.content.Context // 导入上下文类
import android.location.Location // 导入位置类
import android.location.LocationManager // 导入系统原生定位管理类
import android.os.Bundle // 导入用于处理旧版状态回调的类
import com.google.android.gms.location.LocationServices // 导入 Google 定位服务
import com.google.android.gms.location.Priority // 导入优先级设置
import com.google.android.gms.location.CurrentLocationRequest // 导入位置请求配置
import kotlinx.coroutines.suspendCancellableCoroutine // ✅ 核心导入：用于将旧版异步回调转为现代协程挂起
import kotlinx.coroutines.tasks.await // 导入协程等待
import kotlinx.coroutines.withTimeoutOrNull // 导入超时处理
import kotlin.coroutines.resume // ✅ 核心导入：用于手动恢复协程执行

// ✅ 全能型定位辅助：针对无 Google 框架、老旧国产手机进行了极致适配
object LocationHelper {

    /**
     * ✅ 获取当前位置：采用四重保险策略
     */
    @SuppressLint("MissingPermission") // 权限已在 UI 层申请，此处假设已有权限
    suspend fun getCurrentLocation(context: Context): Location? {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // --- 第一步：尝试读取 Google 缓存（针对有 GMS 的手机） ---
        try {
            val lastLocation = fusedLocationClient.lastLocation.await()
            if (lastLocation != null) return lastLocation 
        } catch (e: Exception) {}

        // --- 第二步：尝试 Google 实时定位（设置 3 秒超时） ---
        val gmsLocation = withTimeoutOrNull(3000) {
            try {
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .build()
                fusedLocationClient.getCurrentLocation(request, null).await()
            } catch (e: Exception) { null }
        }
        if (gmsLocation != null) return gmsLocation

        // --- 第三步：尝试系统原生缓存（针对国产手机的历史记录） ---
        val lastSystemLoc = try {
            val provider = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) 
                LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
            locationManager.getLastKnownLocation(provider)
        } catch (e: Exception) { null }
        if (lastSystemLoc != null) return lastSystemLoc

        // --- 第四步：✅ 终极方案：强制发起系统底层搜星（5 秒超时） ---
        // 专门解决“旧手机且无缓存”的情况。它会真正命令硬件去搜索此时的位置。
        return withTimeoutOrNull(5000) {
            suspendCancellableCoroutine { continuation ->
                // 定义定位监听器
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        // 1. 只要拿到位置，立即停止搜星以节省电量
                        locationManager.removeUpdates(this)
                        // 2. 恢复挂起的协程，将位置结果传回去
                        if (continuation.isActive) continuation.resume(location)
                    }
                    // 以下为适配旧版 Android 系统必须实现的存根方法
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {}
                }
                
                try {
                    // 3. 决定定位来源：优先网络定位（室内快），其次 GPS
                    val provider = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                        LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
                    
                    // 4. 正式向系统请求位置更新
                    locationManager.requestLocationUpdates(provider, 0L, 0f, listener)
                } catch (e: Exception) {
                    // 如果启动失败，返回 null
                    if (continuation.isActive) continuation.resume(null)
                }
                
                // 5. 如果 5 秒内没搜到，协程会被取消，此时必须移除监听器，防止内存泄漏
                continuation.invokeOnCancellation {
                    locationManager.removeUpdates(listener)
                }
            }
        }
    }
}
