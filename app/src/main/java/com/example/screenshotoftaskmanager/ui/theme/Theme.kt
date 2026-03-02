package com.example.screenshotoftaskmanager.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(   //声明深色主题配色方案变量
    primary = Purple80,    //主色
    secondary = PurpleGrey80,   //次色
    tertiary = Pink80     //第三色
)

private val LightColorScheme = lightColorScheme(      //同理声明浅色配色方案
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun ScreenshotofTaskManagerTheme(         //定义主题设置函数
    darkTheme: Boolean = isSystemInDarkTheme(),     //获取安卓系统本身的深色或者浅色状态 若系统是深色模式，其值为真，否则为假；
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,    //动态颜色开启
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme     //使用普通深色方案
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}