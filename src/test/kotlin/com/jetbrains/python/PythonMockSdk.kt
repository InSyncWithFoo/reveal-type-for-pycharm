package com.jetbrains.python

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.codeInsight.typing.PyTypeShed.findRootsForLanguageLevel
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import java.io.File
import java.util.*
import java.util.function.Consumer


// Copied from:
// https://github.com/JetBrains/intellij-community/blob/eeb68d528244/python/testSrc/com/jetbrains/python/PythonMockSdk.java
@Suppress("warnings")
object PythonMockSdk {
    
    fun create(): Sdk {
        return create(LanguageLevel.getLatest())
    }
    
    fun create(sdkPath: String): Sdk {
        return create(sdkPath, LanguageLevel.getLatest())
    }
    
    fun create(level: LanguageLevel, vararg additionalRoots: VirtualFile): Sdk {
        return create(PythonTestUtil.testDataPath + "/MockSdk", level, *additionalRoots)
    }
    
    private fun create(sdkPath: String, level: LanguageLevel, vararg additionalRoots: VirtualFile): Sdk {
        val sdkName = "Mock " + PyNames.PYTHON_SDK_ID_NAME + " " + level.toPythonVersion()
        return create(sdkName, sdkPath, PyMockSdkType(level), level, *additionalRoots)
    }
    
    fun create(
        sdkName: String,
        sdkPath: String,
        sdkType: SdkTypeId,
        level: LanguageLevel,
        vararg additionalRoots: VirtualFile
    ): Sdk {
        val sdk = ProjectJdkTable.getInstance().createSdk(sdkName, sdkType)
        val sdkModificator = sdk.sdkModificator
        sdkModificator.homePath = "$sdkPath/bin/python"
        sdkModificator.versionString = toVersionString(level)
        
        createRoots(sdkPath, level).forEach(Consumer { vFile: VirtualFile? ->
            sdkModificator.addRoot(
                vFile!!, OrderRootType.CLASSES
            )
        })
        
        Arrays.asList(*additionalRoots).forEach(Consumer { vFile: VirtualFile? ->
            sdkModificator.addRoot(
                vFile!!, OrderRootType.CLASSES
            )
        })
        
        val application = ApplicationManager.getApplication()
        val runnable = Runnable { sdkModificator.commitChanges() }
        if (application.isDispatchThread) {
            application.runWriteAction(runnable)
        } else {
            application.invokeAndWait { application.runWriteAction(runnable) }
        }
        sdk.putUserData(PythonSdkType.MOCK_PY_MARKER_KEY, true)
        return sdk
        
        // com.jetbrains.python.psi.resolve.PythonSdkPathCache.getInstance() corrupts SDK, so have to clone
        // return sdk.clone();
    }
    
    private fun createRoots(@NonNls mockSdkPath: String, level: LanguageLevel): List<VirtualFile> {
        val result = ArrayList<VirtualFile>()
        
        val localFS = LocalFileSystem.getInstance()
        ContainerUtil.addIfNotNull(result, localFS.refreshAndFindFileByIoFile(File(mockSdkPath, "Lib")))
        ContainerUtil.addIfNotNull(
            result,
            localFS.refreshAndFindFileByIoFile(
                File(
                    mockSdkPath,
                    PythonSdkUtil.SKELETON_DIR_NAME
                )
            )
        )
        
        ContainerUtil.addIfNotNull(result, PyUserSkeletonsUtil.getUserSkeletonsDirectory())
        
        result.addAll(findRootsForLanguageLevel(level))
        
        return result
    }
    
    private fun toVersionString(level: LanguageLevel): String {
        return "Python " + level.toPythonVersion()
    }
    
    private class PyMockSdkType(private val myLevel: LanguageLevel) : SdkTypeId {
        
        override fun getName(): String {
            return PyNames.PYTHON_SDK_ID_NAME
        }
        
        override fun getVersionString(sdk: Sdk): String {
            return toVersionString(myLevel)
        }
        
        override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {
        }
        
        override fun loadAdditionalData(currentSdk: Sdk, additional: Element): SdkAdditionalData? {
            return null
        }
        
    }
    
}
