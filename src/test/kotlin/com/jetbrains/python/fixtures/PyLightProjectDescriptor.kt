package com.jetbrains.python.fixtures

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import com.jetbrains.python.PythonMockSdk.create
import com.jetbrains.python.PythonTestUtil.testDataPath
import com.jetbrains.python.psi.LanguageLevel


// Copied from:
// https://github.com/JetBrains/intellij-community/blob/eeb68d528244/python/testSrc/com/jetbrains/python/fixtures/PyLightProjectDescriptor.java#L26
@Suppress("warnings")
class PyLightProjectDescriptor private constructor(
    private val myName: String?,
    private val myLevel: LanguageLevel
) : LightProjectDescriptor() {
    
    constructor(level: LanguageLevel) : this(null, level)
    
    constructor(name: String) : this(name, LanguageLevel.getLatest())
    
    override fun getSdk(): Sdk? {
        return if (myName == null) {
            create(myLevel, *additionalRoots)
        } else {
            create(testDataPath + "/" + myName)
        }
    }
    
    protected val additionalRoots: Array<VirtualFile>
        /**
         * @return additional roots to add to mock python
         * @apiNote ignored when name is provided.
         */
        get() = VirtualFile.EMPTY_ARRAY
    
    protected fun createLibrary(model: ModifiableRootModel, name: String?, path: String) {
        val modifiableModel = model.moduleLibraryTable.createLibrary(name).modifiableModel
        val home = LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManager.getHomePath() + path)
        
        modifiableModel.addRoot(home!!, OrderRootType.CLASSES)
        modifiableModel.commit()
    }
    
}
