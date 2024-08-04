package insyncwithfoo.revealtype

import com.intellij.testFramework.fixtures.BasePlatformTestCase


class RevealTypeInspectionTest : BasePlatformTestCase() {
    
    private val fixture by ::myFixture
    
    override fun getTestDataPath() = "src/test/testData"
    
    fun `test - basic`() {
        doTest("basic.py")
    }
    
    fun `test - generics`() {
        doTest("generics.py")
    }
    
    fun `test - invalid calls`() {
        doTest("invalid_calls.py")
    }
    
    private fun doTest(fileName: String) {
        val (checkWarnings, checkInfos, checkWeakWarnings) = Triple(true, false, true)
        
        fixture.configureByFile(fileName)
        
        fixture.enableInspections(RevealTypeInspection())
        fixture.checkHighlighting(checkWarnings, checkInfos, checkWeakWarnings)
    }
    
}
