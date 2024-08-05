package insyncwithfoo.revealtype

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.jetbrains.python.fixtures.PyTestCase


class RevealTypeInspectionTest : PyTestCase() {
    
    private val fixture: CodeInsightTestFixture
        get() = myFixture!!
    
    override val testDataPath: String
        get() = "src/test/testData"
    
    fun `test - basic`() {
        doTest("basic.py")
    }
    
    fun `test - generics`() {
        doTest("generics.py")
    }
    
    fun `test - callables`() {
        doTest("callables.py")
    }
    
    fun `test - invalid calls`() {
        doTest("invalid_calls.py")
    }
    
    private fun doTest(fileName: String) {
        fixture.configureByFile(fileName)
        
        fixture.enableInspections(RevealTypeInspection())
        fixture.checkHighlighting()
    }
    
}
