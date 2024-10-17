package insyncwithfoo.revealtype

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.types.TypeEvalContext


private typealias Arguments = Array<PyExpression>


private val Arguments.onlyPositionalOrNull: PyExpression?
    get() = singleOrNull()?.takeIf { it !is PyKeywordArgument }


private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {
    
    private val context by ::myTypeEvalContext
    
    override fun visitPyCallExpression(node: PyCallExpression) {
        if (node.callee?.name != "reveal_type") {
            return
        }
        
        val arguments = node.argumentList?.arguments
        
        when (val argument = arguments?.onlyPositionalOrNull) {
            null -> invalidCall(node)
            else -> revealType(argument)
        }
    }
    
    private fun invalidCall(node: PyCallExpression) {
        val message = message("tooltip.invalidCall")
        
        registerProblem(node, message, ProblemHighlightType.WARNING)
    }
    
    private fun revealType(argument: PyExpression) {
        val argumentType = renderArgumentType(argument)
        val message = message("tooltip.message", argumentType)
        
        registerProblem(argument, message, ProblemHighlightType.WEAK_WARNING)
    }
    
    private fun renderArgumentType(argument: PyExpression): String {
        val argumentType = context.getType(argument)
        
        // TODO: Allow choosing between .getVerboseTypeName(), .getTypeName() and .getTypeHint().
        return PythonDocumentationProvider.getVerboseTypeName(argumentType, context)
    }
    
}


internal class RevealTypeInspection : PyInspection() {
    
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor =
        Visitor(holder, PyInspectionVisitor.getContext(session))
    
}
