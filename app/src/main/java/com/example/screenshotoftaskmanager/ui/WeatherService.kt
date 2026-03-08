package com.example.screenshotoftaskmanager.ui // 定义包名

import retrofit2.Retrofit // 导入 Retrofit 核心类
import retrofit2.converter.gson.GsonConverterFactory // 导入 Gson 转换器工厂
import retrofit2.http.GET // 导入 GET 请求注解
import retrofit2.http.Query // 导入查询参数注解

// ✅ 定义从 API 返回的天气数据结构（JSON 解析模型）
data class WeatherResponse(
    val lives: List<LiveWeather> // 高德天气 API 返回的是一个列表 天气接口返回的json格式
)

data class LiveWeather(          //表示一条天气信息
    val city: String, // 城市名称
    val weather: String, // 天气现象（如“晴”、“雨”）
    val temperature: String, // 实时温度
    val winddirection: String, // 风向
    val reporttime: String // 发布时间
)

// ✅ 新增：定义逆地理编码（经纬度转城市码）的数据结构
// 对应高德 API 的 JSON 层级结构
data class GeocodeResponse(       //
    val regeocode: RegeocodeData? // 返回的地理反查信息
)

data class RegeocodeData(
    val addressComponent: AddressComponent? // 地址组件信息 json中间层
)

data class AddressComponent(
    val adcode: String? // 核心数据：城市行政区划代码邮编（如 110000）
)

// ✅ 定义 Retrofit API接口：整合天气和逆地理编码功能
interface WeatherApi {
    // 1. 获取天气信息接口
    @GET("https://restapi.amap.com/v3/weather/weatherInfo")   //get是http get请求方式 请求真实url
    suspend fun getWeather(       //suspend 说明是协程网络请求 不会阻塞主线程函数执行到一半可以暂停，等结果回来再继续执行
        @Query("key") apiKey: String,     //query 是url参数
        @Query("city") cityCode: String,
        @Query("extensions") extensions: String = "base"
    ): WeatherResponse

    // 2. ✅ 新增：逆地理编码接口，负责将经纬度坐标转为城市代码
    @GET("https://restapi.amap.com/v3/geocode/regeo") 
    suspend fun getAdcode(   //协程请求
        @Query("key") apiKey: String,
        @Query("location") location: String // 参数格式要求为 "经度,纬度"
    ): GeocodeResponse
}

// ✅ 封装网络请求的 Repository 单例对象   这是数据仓库层 封装网络请求 处理异常 返回ui需要的数据
object WeatherRepository {
    private const val BASE_URL = "https://restapi.amap.com/v3/" // 设置基础网址
    private const val MY_KEY = "c12749063b1d0005ef59c75a4a56ee49" // 使用你提供的高德 API 密钥

    // 初始化 Retrofit 实例，配置 Gson 自动解析 JSON
    private val retrofit = Retrofit.Builder()     //retrofit初始化
        .baseUrl(BASE_URL)      //base url是所有请求的基础地址
        .addConverterFactory(GsonConverterFactory.create())   //作用JSON → Kotlin对象
        .build()     //作用：创建retrofit 网络客户端

    // 创建 API 业务类
    private val api = retrofit.create(WeatherApi::class.java)       //自动生成WeatherApi实现类 以后调用就会自动发送http请求

    /**
     * ✅ 新功能：根据经纬度实时查询所在城市的 adcode
     * @param longitude 经度
     * @param latitude 纬度
     */
    suspend fun getCityCodeByLocation(longitude: Double, latitude: Double): String {
        return try {
            val locationStr = "$longitude,$latitude" // 拼接经纬度成 API 要求的字符串
            val response = api.getAdcode(apiKey = MY_KEY, location = locationStr)       //调用api
            // 提取返回结果中的 adcode，如果没拿到则默认返回北京代码 "110000"
            response.regeocode?.addressComponent?.adcode ?: "110000"
        } catch (e: Exception) {
            "110000" // 网络异常时兜底返回北京
        }
    }

    /**
     * 获取指定城市的实时天气
     * @param cityCode 城市代码（默认为北京）
     */
    suspend fun fetchWeather(cityCode: String = "110000"): String {    //获取天气  这里11000的作用是若果没有传citycode就自动使用110000
        return try {
            val response = api.getWeather(apiKey = MY_KEY, cityCode = cityCode)
            val live = response.lives.firstOrNull() // 获取列表中的首条天气
            if (live != null) {
                // 拼接显示格式：图标 + 城市 + 天气描述 + 温度
                "☀️ ${live.city} ${live.weather} ${live.temperature}°C" 
            } else {
                "⚠️ 数据解析失败" 
            }
        } catch (e: Exception) {
            "❌ 天气查询异常: ${e.message}"
        }
    }
}
