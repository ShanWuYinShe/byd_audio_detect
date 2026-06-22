# 保留领域模型（数据类被 UI/序列化路径引用，避免 R8 裁剪或重命名造成反射失败）
-keep class com.byd.audiochanneldetector.domain.model.** { *; }

# Hilt：保留注入入口与 ViewModel 构造函数
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep,allowobfuscation @dagger.hilt.android.HiltAndroidApp class *
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { <init>(...); }

# 保留运行时注解、泛型签名、内部类信息（Compose 运行时与反射所需）
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeVisibleTypeAnnotations, AnnotationDefault
-keepattributes Exceptions, Deprecated, ExceptionsWithMessage

# Kotlin 元数据（Compose 编译器插件依赖）
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { public <methods>; }

# 枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
