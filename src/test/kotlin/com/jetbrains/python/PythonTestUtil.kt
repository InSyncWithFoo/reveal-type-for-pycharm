package com.jetbrains.python


// Copied from:
// https://github.com/JetBrains/intellij-community/blob/d335ac32c4d8/python/testSrc/com/jetbrains/python/PythonTestUtil.java
@Suppress("warnings")
object PythonTestUtil {
    val testDataPath: String
        get() = PythonHelpersLocator.getPythonCommunityPath().toString() + "/testData"
}
