package com.jetbrains.python.fixtures

import com.google.common.base.Joiner
import com.google.common.collect.Lists
import com.intellij.application.options.CodeStyle
import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.FilePropertyPusher
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.usages.Usage
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.util.CommonProcessors
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.codeInsight.completion.PyModuleNameCompletionContributor
import com.jetbrains.python.documentation.PyDocumentationSettings
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.documentation.docstrings.DocStringFormat
import com.jetbrains.python.namespacePackages.PyNamespacePackagesService
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyFileImpl
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher
import com.jetbrains.python.psi.search.PySearchUtilBase
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.PythonSdkUtil
import org.junit.Assert
import java.io.File
import java.util.*
import java.util.function.Consumer


// Copied from:
// https://github.com/JetBrains/intellij-community/blob/eeb68d528244/python/testSrc/com/jetbrains/python/fixtures/PyTestCase.java
@Suppress("warnings")
@TestDataPath("\$CONTENT_ROOT/../testData/")
abstract class PyTestCase : UsefulTestCase() {
    
    protected var myFixture: CodeInsightTestFixture? = null
    
    protected fun assertProjectFilesNotParsed(currentFile: PsiFile) {
        assertRootNotParsed(currentFile, myFixture!!.tempDirFixture.getFile(".")!!, null)
    }
    
    protected fun assertProjectFilesNotParsed(context: TypeEvalContext) {
        assertRootNotParsed(context.origin!!, myFixture!!.tempDirFixture.getFile(".")!!, context)
    }
    
    protected fun assertSdkRootsNotParsed(currentFile: PsiFile) {
        val testSdk = PythonSdkUtil.findPythonSdk(currentFile)
        for (root in testSdk!!.rootProvider.getFiles(OrderRootType.CLASSES)) {
            assertRootNotParsed(currentFile, root, null)
        }
    }
    
    private fun assertRootNotParsed(currentFile: PsiFile, root: VirtualFile, context: TypeEvalContext?) {
        for (file in VfsUtil.collectChildrenRecursively(root)) {
            val pyFile = PyUtil.`as`(myFixture!!.psiManager.findFile(file), PyFile::class.java)
            if (pyFile != null && pyFile != currentFile && (context == null || !context.maySwitchToAST(pyFile))) {
                assertNotParsed(pyFile)
            }
        }
    }
    
    /**
     * Reformats currently configured file.
     */
    protected fun reformatFile() {
        WriteCommandAction.runWriteCommandAction(null) { doPerformFormatting() }
    }
    
    @Throws(IncorrectOperationException::class) private fun doPerformFormatting() {
        val file = myFixture!!.file
        val myTextRange = file.textRange
        CodeStyleManager.getInstance(myFixture!!.project)
            .reformatText(file, myTextRange.startOffset, myTextRange.endOffset)
    }
    
    @Throws(Exception::class) override fun setUp() {
        super.setUp()
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val fixtureBuilder = factory.createLightFixtureBuilder(projectDescriptor, getTestName(false))
        val fixture = fixtureBuilder.fixture
        myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, createTempDirFixture())
        myFixture!!.testDataPath = this.testDataPath
        myFixture!!.setUp()
    }
    
    /**
     * @return fixture to be used as temporary dir.
     */
    protected fun createTempDirFixture(): TempDirTestFixture {
        return LightTempDirTestFixtureImpl(true) // "tmp://" dir by default
    }
    
    protected fun runWithAdditionalFileInLibDir(
        relativePath: String,
        text: String,
        fileConsumer: Consumer<VirtualFile>
    ) {
        val sdk = PythonSdkUtil.findPythonSdk(myFixture!!.module)
        val libDir = PySearchUtilBase.findLibDir(sdk!!)
        if (libDir != null) {
            runWithAdditionalFileIn(relativePath, text, libDir, fileConsumer)
        } else {
            createAdditionalRootAndRunWithIt(
                sdk,
                "Lib",
                OrderRootType.CLASSES
            ) { root: VirtualFile ->
                runWithAdditionalFileIn(
                    relativePath,
                    text,
                    root,
                    fileConsumer
                )
            }
        }
    }
    
    protected fun runWithAdditionalFileInSkeletonDir(
        relativePath: String,
        text: String,
        fileConsumer: Consumer<VirtualFile>
    ) {
        val sdk = PythonSdkUtil.findPythonSdk(myFixture!!.module)
        val skeletonsDir = PythonSdkUtil.findSkeletonsDir(sdk!!)
        if (skeletonsDir != null) {
            runWithAdditionalFileIn(relativePath, text, skeletonsDir, fileConsumer)
        } else {
            createAdditionalRootAndRunWithIt(
                sdk,
                PythonSdkUtil.SKELETON_DIR_NAME,
                PythonSdkUtil.BUILTIN_ROOT_TYPE
            ) { root: VirtualFile ->
                runWithAdditionalFileIn(
                    relativePath,
                    text,
                    root,
                    fileConsumer
                )
            }
        }
    }
    
    protected fun runWithAdditionalClassEntryInSdkRoots(directory: VirtualFile, runnable: Runnable) {
        val sdk = PythonSdkUtil.findPythonSdk(myFixture!!.module)
        assertNotNull(sdk)
        runWithAdditionalRoot(
            sdk!!, directory, OrderRootType.CLASSES
        ) { `__`: VirtualFile? -> runnable.run() }
    }
    
    protected fun runWithAdditionalClassEntryInSdkRoots(relativeTestDataPath: String, runnable: Runnable) {
        val absPath = this.testDataPath + "/" + relativeTestDataPath
        val testDataDir = StandardFileSystems.local().findFileByPath(absPath)
        assertNotNull("Additional class entry directory '$absPath' not found", testDataDir)
        runWithAdditionalClassEntryInSdkRoots(testDataDir!!, runnable)
    }
    
    protected open val testDataPath: String
        get() = PythonTestUtil.testDataPath
    
    @Throws(Exception::class) override fun tearDown() {
        try {
            PyNamespacePackagesService.getInstance(myFixture!!.module).resetAllNamespacePackages()
            PyModuleNameCompletionContributor.ENABLED = true
            setLanguageLevel(null)
            myFixture!!.tearDown()
            myFixture = null
            FilePropertyPusher.EP_NAME.findExtensionOrFail(PythonLanguageLevelPusher::class.java)
                .flushLanguageLevelCache()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }
    
    protected val projectDescriptor: LightProjectDescriptor?
        get() = ourPyLatestDescriptor
    
    protected fun findReferenceBySignature(signature: String?): PsiReference? {
        val pos = findPosBySignature(signature)
        return findReferenceAt(pos)
    }
    
    protected fun findReferenceAt(pos: Int): PsiReference? {
        return myFixture!!.file.findReferenceAt(pos)
    }
    
    protected fun findPosBySignature(signature: String?): Int {
        return PsiDocumentManager.getInstance(myFixture!!.project).getDocument(myFixture!!.file)!!
            .text.indexOf(signature!!)
    }
    
    private fun setLanguageLevel(languageLevel: LanguageLevel?) {
        PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture!!.project, languageLevel)
        IndexingTestUtil.waitUntilIndexesAreReady(myFixture!!.project)
    }
    
    protected fun runWithLanguageLevel(languageLevel: LanguageLevel, runnable: Runnable) {
        setLanguageLevel(languageLevel)
        try {
            runnable.run()
        } finally {
            setLanguageLevel(null)
        }
    }
    
    protected fun runWithDocStringFormat(format: DocStringFormat, runnable: Runnable) {
        val settings = PyDocumentationSettings.getInstance(myFixture!!.module)
        val oldFormat = settings.format
        settings.format = format
        try {
            runnable.run()
        } finally {
            settings.format = oldFormat
        }
    }
    
    protected fun runWithSourceRoots(sourceRoots: List<VirtualFile?>, runnable: Runnable) {
        val module = myFixture!!.module
        sourceRoots.forEach(Consumer { root: VirtualFile? ->
            PsiTestUtil.addSourceRoot(
                module,
                root!!
            )
        })
        try {
            runnable.run()
        } finally {
            sourceRoots.forEach(Consumer { root: VirtualFile? ->
                PsiTestUtil.removeSourceRoot(
                    module,
                    root!!
                )
            })
        }
    }
    
    /**
     * @return class by its name from file
     */
    protected fun getClassByName(name: String): PyClass {
        return myFixture!!.findElementByText("class $name", PyClass::class.java)
    }
    
    /**
     * @see .moveByText
     */
    protected fun moveByText(testToFind: String) {
        moveByText(myFixture!!, testToFind)
    }
    
    /**
     * Finds all usages of element. Works much like method in [com.intellij.testFramework.fixtures.CodeInsightTestFixture.findUsages],
     * but supports [com.intellij.find.findUsages.CustomUsageSearcher] and [com.intellij.psi.search.searches.ReferencesSearch] as well
     *
     * @param element what to find
     * @return usages
     */
    protected fun findUsage(element: PsiElement): Collection<PsiElement?> {
        val result: MutableCollection<PsiElement?> = ArrayList()
        val usageCollector = CommonProcessors.CollectProcessor<Usage>()
        for (searcher in CustomUsageSearcher.EP_NAME.extensions) {
            searcher.processElementUsages(element, usageCollector, FindUsagesOptions(myFixture!!.project))
        }
        for (usage in usageCollector.results) {
            if (usage is PsiElementUsage) {
                result.add(usage.element)
            }
        }
        for (reference in ReferencesSearch.search(element).findAll()) {
            result.add(reference.element)
        }
        
        for (info in myFixture!!.findUsages(element)) {
            result.add(info.element)
        }
        
        return result
    }
    
    /**
     * Returns elements certain element allows to navigate to (emulates CTRL+Click, actually).
     * You need to pass element as argument or
     * make sure your fixture is configured for some element (see [com.intellij.testFramework.fixtures.CodeInsightTestFixture.getElementAtCaret])
     *
     * @param element element to fetch navigate elements from (may be null: element under caret would be used in this case)
     * @return elements to navigate to
     */
    protected fun getElementsToNavigate(element: PsiElement?): Set<PsiElement?> {
        val result: MutableSet<PsiElement?> = HashSet()
        val elementToProcess = (if ((element != null)) element else myFixture!!.elementAtCaret)
        for (reference in elementToProcess.references) {
            val directResolve = reference.resolve()
            if (directResolve != null) {
                result.add(directResolve)
            }
            if (reference is PsiPolyVariantReference) {
                for (resolveResult in reference.multiResolve(true)) {
                    result.add(resolveResult.element)
                }
            }
        }
        return result
    }
    
    /**
     * Clears provided file
     *
     * @param file file to clear
     */
    protected fun clearFile(file: PsiFile) {
        CommandProcessor.getInstance().executeCommand(
            myFixture!!.project,
            {
                ApplicationManager.getApplication()
                    .runWriteAction {
                        for (element in file.children) {
                            element.delete()
                        }
                    }
            }, null, null
        )
    }
    
    /**
     * Runs refactoring using special handler
     *
     * @param handler handler to be used
     */
    protected fun refactorUsingHandler(handler: RefactoringActionHandler) {
        val editor = myFixture!!.editor
        assertInstanceOf(editor, EditorEx::class.java)
        handler.invoke(myFixture!!.project, editor, myFixture!!.file, (editor as EditorEx).dataContext)
    }
    
    /**
     * Clicks certain button in document on caret position
     *
     * @param action what button to click (const from [IdeActions]) (btw, there should be some way to express it using annotations)
     * @see IdeActions
     */
    protected fun pressButton(action: String) {
        CommandProcessor.getInstance().executeCommand(
            myFixture!!.project,
            {
                myFixture!!.performEditorAction(
                    action
                )
            }, "", null
        )
    }
    
    protected val commonCodeStyleSettings: CommonCodeStyleSettings
        get() = codeStyleSettings.getCommonSettings(PythonLanguage.getInstance())
    
    protected val codeStyleSettings: CodeStyleSettings
        get() = CodeStyle.getSettings(myFixture!!.project)
    
    protected val indentOptions: IndentOptions
        get() = commonCodeStyleSettings.indentOptions!!
    
    /**
     * When you have more than one completion variant, you may use this method providing variant to choose.
     * It only works for one caret (multiple carets not supported) and since it puts tab after completion, be sure to limit
     * line somehow (i.e. with comment).
     * <br></br>
     * Example: "user.n[caret]." There are "name" and "nose" fields.
     * By calling this function with "nose" you will end with "user.nose  ".
     */
    protected fun completeCaretWithMultipleVariants(vararg desiredVariants: String) {
        val lookupElements = myFixture!!.completeBasic()
        val lookup = myFixture!!.lookup
        if (lookupElements != null && lookupElements.size > 1) {
            // More than one element returned, check directly because completion can't work in this case
            for (element in lookupElements) {
                val suggestedString = element.lookupString
                if (Arrays.asList(*desiredVariants).contains(suggestedString)) {
                    myFixture!!.lookup.currentItem = element
                    lookup.currentItem = element
                    myFixture!!.completeBasicAllCarets('\t')
                    return
                }
            }
        }
    }
    
    protected val elementAtCaret: PsiElement
        get() {
            val file = myFixture!!.file
            assertNotNull(file)
            return file.findElementAt(myFixture!!.caretOffset)!!
        }
    
    fun addExcludedRoot(rootPath: String?) {
        val dir = myFixture!!.findFileInTempDir(rootPath!!)
        val module = myFixture!!.module
        assertNotNull(dir)
        PsiTestUtil.addExcludedRoot(module, dir)
        Disposer.register(
            myFixture!!.projectDisposable
        ) { PsiTestUtil.removeExcludedRoot(module, dir) }
    }
    
    fun <T> assertContainsInRelativeOrder(actual: Iterable<T>, vararg expected: T?) {
        val actualList: List<T?> = Lists.newArrayList(actual)
        if (expected.size > 0) {
            var prev = expected[0]
            var prevIndex = actualList.indexOf(prev)
            assertTrue(prev.toString() + " is not found in " + actualList, prevIndex >= 0)
            for (i in 1 until expected.size) {
                val next = expected[i]
                val nextIndex = actualList.indexOf(next)
                assertTrue(next.toString() + " is not found in " + actualList, nextIndex >= 0)
                assertTrue(prev.toString() + " should precede " + next + " in " + actualList, prevIndex < nextIndex)
                prev = next
                prevIndex = nextIndex
            }
        }
    }
    
    companion object {
        
        protected val ourPy2Descriptor: PyLightProjectDescriptor = PyLightProjectDescriptor(LanguageLevel.PYTHON27)
        protected val ourPyLatestDescriptor: PyLightProjectDescriptor =
            PyLightProjectDescriptor(LanguageLevel.getLatest())
        
        protected fun getVirtualFileByName(fileName: String): VirtualFile? {
            val path = LocalFileSystem.getInstance().findFileByPath(fileName.replace(File.separatorChar, '/'))
            if (path != null) {
                refreshRecursively(path)
                return path
            }
            return null
        }
        
        private fun runWithAdditionalFileIn(
            relativePath: String,
            text: String,
            dir: VirtualFile,
            fileConsumer: Consumer<VirtualFile>
        ) {
            val file = VfsTestUtil.createFile(dir, relativePath, text)
            try {
                fileConsumer.accept(file)
            } finally {
                VfsTestUtil.deleteFile(file)
            }
        }
        
        private fun createAdditionalRootAndRunWithIt(
            sdk: Sdk,
            rootRelativePath: String,
            rootType: OrderRootType,
            rootConsumer: Consumer<VirtualFile>
        ) {
            val tempRoot = VfsTestUtil.createDir(sdk.homeDirectory!!.parent.parent, rootRelativePath)
            try {
                runWithAdditionalRoot(sdk, tempRoot, rootType, rootConsumer)
            } finally {
                VfsTestUtil.deleteFile(tempRoot)
            }
        }
        
        private fun runWithAdditionalRoot(
            sdk: Sdk,
            root: VirtualFile,
            rootType: OrderRootType,
            rootConsumer: Consumer<VirtualFile>
        ) {
            WriteAction.run<RuntimeException> {
                val modificator = sdk.sdkModificator
                assertNotNull(modificator)
                modificator.addRoot(root, rootType)
                modificator.commitChanges()
            }
            IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects()
            try {
                rootConsumer.accept(root)
            } finally {
                WriteAction.run<RuntimeException> {
                    val modificator = sdk.sdkModificator
                    assertNotNull(modificator)
                    modificator.removeRoot(root, rootType)
                    modificator.commitChanges()
                }
                IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects()
            }
        }
        
        protected fun assertNotParsed(file: PsiFile) {
            assertInstanceOf(file, PyFileImpl::class.java)
            assertNull(
                "Operations should have been performed on stubs but caused file to be parsed: " + file.virtualFile.path,
                (file as PyFileImpl).treeElement
            )
        }
        
        /**
         * Finds some text and moves cursor to it (if found)
         *
         * @param fixture    test fixture
         * @param testToFind text to find
         * @throws AssertionError if element not found
         */
        fun moveByText(fixture: CodeInsightTestFixture, testToFind: String) {
            val element = checkNotNull(
                fixture.findElementByText(
                    testToFind,
                    PsiElement::class.java
                )
            ) { "No element found by text: $testToFind" }
            fixture.editor.caretModel.moveToOffset(element.textOffset)
        }
        
        /**
         * Compares sets with string sorting them and displaying one-per-line to make comparision easier
         *
         * @param message  message to display in case of error
         * @param actual   actual set
         * @param expected expected set
         */
        protected fun compareStringSets(
            message: String,
            actual: Set<String>,
            expected: Set<String>
        ) {
            val joiner = Joiner.on("\n")
            Assert.assertEquals(message, joiner.join(TreeSet(actual)), joiner.join(TreeSet(expected)))
        }
        
        fun assertType(expectedType: String, element: PyTypedElement, context: TypeEvalContext) {
            assertType("Failed in $context context", expectedType, element, context)
        }
        
        fun assertType(
            message: String,
            expectedType: String,
            element: PyTypedElement,
            context: TypeEvalContext
        ) {
            val actual = context.getType(element)
            val actualType = PythonDocumentationProvider.getTypeName(actual, context)
            assertEquals(message, expectedType, actualType)
        }
        
    }
    
}
