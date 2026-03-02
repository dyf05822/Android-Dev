package com.example.screenshotoftaskmanager.ui // 定义包名

import retrofit2.Retrofit // 导入 Retrofit 核心类
import retrofit2.converter.gson.GsonConverterFactory // 导入 Gson 转换器工厂
import retrofit2.http.GET // 导入 GET 请求注解
import retrofit2.http.Query // 导入查询参数注解

// ✅ 定义从 API 返回的天气数据结构（JSON 解析模型）
data class WeatherResponse(
    val lives: List<LiveWeather> // 高德天气 API 返回的是一个列表
)

data class LiveWeather(
    val city: String, // 城市名称
    val weather: String, // 天气现象（如“晴”、“雨”）
    val temperature: String, // 实时温度
    val winddirection: String, // 风向
    val reporttime: String // 发布时间
)

// ✅ 定义 Retrofit 接口：描述我们要访问的具体路径和参数
interface WeatherApi {
    @GET("weatherInfo") // 设置请求的相对路径
    suspend fun getWeather( // 使用 suspend 关键字，支持在协程中异步调用
        @Query("key") apiKey: String, // 传入你的 API 密钥
        @Query("city") cityCode: String, // 传入城市代码（如北京是 110000）
        @Query("extensions") extensions: String = "base" // 基础天气信息
    ): WeatherResponse // 返回我们定义好的数据模型
}

// ✅ 创建网络请求的单例对象
object WeatherRepository {
    // 高德地图天气 API 的基地址
    private const val BASE_URL = "https://restapi.amap.com/v3/weather/"
    
    // 初始化 Retrofit 实例
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL) // 设置基地址
        .addConverterFactory(GsonConverterFactory.create()) // 设置 Gson 解析器，自动将 JSON 转为对象
        .build()

    // 创建 API 实现类
    private val api = retrofit.create(WeatherApi::class.java)

    // 封装一个简单的获取天气函数
    // 注意：这里的 key 已经填入了你提供的真实 Key
    suspend fun fetchWeather(cityCode: String = "110000"): String {         //此处默认的是北京的邮政编码
        return try {
            // 调用接口获取数据。这里已填入你提供的真实高德 API Key
            val response = api.getWeather(apiKey = "c12749063b1d0005ef59c75a4a56ee49", cityCode = cityCode)
            val live = response.lives.firstOrNull() // 获取返回列表中的第一个天气数据
            if (live != null) {
                "☀️ ${live.city} ${live.weather} ${live.temperature}°C" // 拼接成我们要显示的字符串
            } else {
                "⚠️ 天气数据为空" // 如果列表为空的提示
            }
        } catch (e: Exception) {
            "❌ 天气加载失败: ${e.message}" // 捕获网络异常（如没网、URL 错误）
        }
    }
}
