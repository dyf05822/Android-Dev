package com.example.screenshotoftaskmanager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(   //创建对象定义字体样式集合
    bodyLarge = TextStyle(     //文本规格
        fontFamily = FontFamily.Default,   //字体类型   （默认字体）
        fontWeight = FontWeight.Normal,       //字体粗细程度  （正常粗细）
        fontSize = 16.sp,     //字号
        lineHeight = 24.sp,     //行高
        letterSpacing = 0.5.sp    //字间距
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)