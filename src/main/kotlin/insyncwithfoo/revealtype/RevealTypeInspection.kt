package insyncwithfoo.revealtype

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext


private typealias Arguments = Array<PyExpression>


private val Arguments?.onlyPositionalElement: PyExpression?
    get() = when {
        this == null || size != 1 -> null
        else -> first().takeIf { it !is PyKeywordArgument }
    }


private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {
    
    private val context by ::myTypeEvalContext
    
    override fun visitPyCallExpression(node: PyCallExpression) {
        if (node.callee?.name != "reveal_type") {
            return
        }
        
        val arguments = node.argumentList?.arguments
        
        when (val onlyArgument = arguments.onlyPositionalElement) {
            null -> invalidCall(node)
            else -> revealType(onlyArgument)
        }
    }
    
    private fun invalidCall(node: PyCallExpression) {
        val message = message("tooltip.invalidCall")
        
        registerProblem(node, message, ProblemHighlightType.WARNING)
    }
    
    private fun revealType(argument: PyExpression) {
        val argumentType = context.getType(argument)
            ?.let { renderArgumentType(it) } ?: message("tooltip.unknownType")
        val message = message("tooltip.message", argumentType)
        
        registerProblem(argument, message, ProblemHighlightType.WEAK_WARNING)
    }
    
    private fun renderArgumentType(argumentType: PyType): String? {
        return argumentType.name
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
