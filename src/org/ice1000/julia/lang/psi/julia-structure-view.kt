package org.ice1000.julia.lang.psi

import com.intellij.ide.structureView.*
import com.intellij.ide.structureView.impl.StructureViewComposite
import com.intellij.ide.structureView.impl.TemplateLanguageStructureViewBuilder
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.pom.Navigatable
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ReflectionUtil
import icons.JuliaIcons
import org.ice1000.julia.lang.JuliaFile
import org.ice1000.julia.lang.JuliaLanguage
import org.ice1000.julia.lang.editing.cutText
import org.ice1000.julia.lang.psi.impl.JuliaFunctionImpl


class JuliaStructureViewFactory : PsiStructureViewFactory {
	override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
		return object : TemplateLanguageStructureViewBuilder(psiFile) {
			override fun createMainView(fileEditor: FileEditor?, mainFile: PsiFile?): StructureViewComposite.StructureViewDescriptor? {
				if (!psiFile.isValid) {
					return null
				}
				val builder = object : TreeBasedStructureViewBuilder() {
					override fun createStructureViewModel(editor: Editor?) = JuliaStructureViewModel(psiFile)
				}
				val view = builder.createStructureView(fileEditor, psiFile.project)
				return StructureViewComposite.StructureViewDescriptor(JuliaLanguage.INSTANCE.displayName, view, JuliaIcons.JULIA_ICON)
			}
		}
	}


	class JuliaStructureViewModel(psiFile: PsiFile) : StructureViewModelBase(psiFile, JuliaStructureViewElement(psiFile)), StructureViewModel.ElementInfoProvider {
		override fun getSuitableClasses() = ourSuitableClasses
		override fun shouldEnterElement(o: Any?) = true
		override fun isAlwaysShowsPlus(element: StructureViewTreeElement) = false
		override fun isAlwaysLeaf(element: StructureViewTreeElement) = when (element) {
			is JuliaFunctionImpl -> true
			else -> false
		}

		companion object {
			val ourSuitableClasses = arrayOf<Class<*>>(
				JuliaFile::class.java,
				JuliaAssignLevelOp::class.java,
				JuliaFunction::class.java,
				JuliaModuleDeclaration::class.java,
				JuliaTypeDeclaration::class.java
			)
		}
	}

	class JuliaStructureViewElement(private val psiElement: PsiElement) :
		PsiTreeElementBase<PsiElement>(psiElement)
		, SortableTreeElement, Navigatable by (psiElement as NavigatablePsiElement) {

		override fun getChildrenBase() = getGrandsonOfYourMother()

		private fun getGrandsonOfYourMother(): List<StructureViewTreeElement> {
			val children = ArrayList<StructureViewTreeElement>()
			psiElement.children
				.filter { it !is LeafPsiElement }//filter EOL
				.forEach { element ->
					println(element.toString() + "${element.isBlock}")
					if (element.isBlock) {
						children.add(JuliaStructureViewElement(element))
					}
					JuliaStructureViewModel.ourSuitableClasses
						.filter { suitableClass -> ReflectionUtil.isAssignable(suitableClass, element.javaClass) && (JuliaStructureViewElement(element) !in children) }
						.mapTo(children) { JuliaStructureViewElement(element) }
				}
			return children.toList()
		}

		override fun getLocationString() = ""
		override fun getIcon(open: Boolean) =
			when (psiElement) {
			//FIXME: JuliaFile needs current ICON
				is JuliaFile -> JuliaIcons.JULIA_ICON
				is JuliaFunction -> JuliaIcons.JULIA_BIG_ICON
				is JuliaModuleDeclaration -> JuliaIcons.JULIA_MODULE_ICON
				is JuliaTypeDeclaration -> JuliaIcons.JULIA_TYPE_ICON
				else -> JuliaIcons.JULIA_ICON
			}

		override fun getPresentableText() = cutText(psiElement.let {
			when (it) {
				is JuliaFile -> it.originalFile.name
				is JuliaFunction -> "fun: ${it.exprList.first().text}"
				is JuliaAssignLevelOp -> it.exprList.first().text
				is JuliaTypeDeclaration -> "type: ${it.exprList.first().text}"
				is JuliaModuleDeclaration -> "module: ${it.symbol.text}"
				else -> "..."
			}
		}, 50)

		override fun navigate(requestFocus: Boolean) {
			if (psiElement is NavigationItem) {
				(psiElement as NavigationItem).navigate(requestFocus)
			}
		}

		override fun getValue() = psiElement
		override fun canNavigate() = psiElement is NavigationItem && (psiElement as NavigationItem).canNavigate()
		override fun canNavigateToSource() = psiElement is NavigationItem && (psiElement as NavigationItem).canNavigateToSource()
		override fun getAlphaSortKey() = (psiElement as? PsiNamedElement)?.name.orEmpty()
	}
}

val PsiElement.isBlock
	get() = when (this) {
		is JuliaFile,
		is JuliaStatements,
		is JuliaModuleDeclaration,
		is JuliaFunction,
		is JuliaTypeDeclaration
//		is JuliaIfExpr
		-> true
		else -> false
	}