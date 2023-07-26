package cc.unitmesh.idea.provider

import cc.unitmesh.devti.context.chunks.SimilarChunksWithPaths
import cc.unitmesh.devti.gui.chat.ChatActionType
import cc.unitmesh.devti.prompting.VcsPrompting
import cc.unitmesh.devti.prompting.model.CustomPromptConfig
import cc.unitmesh.devti.provider.ContextPrompter
import cc.unitmesh.devti.provider.context.ChatContextProvider
import cc.unitmesh.devti.provider.context.ChatCreationContext
import cc.unitmesh.devti.provider.context.ChatOrigin
import cc.unitmesh.devti.settings.AutoDevSettingsState
import cc.unitmesh.idea.flow.MvcContextService
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.runBlocking

class JvmIdeaContextPrompter : ContextPrompter() {
    private var additionContext: String = ""
    private val autoDevSettingsState = AutoDevSettingsState.getInstance()
    private var customPromptConfig: CustomPromptConfig? = null
    private lateinit var mvcContextService: MvcContextService
    private var fileName = ""
    private lateinit var changeListManager: ChangeListManager

    private fun langSuffix(): String = when (lang.lowercase()) {
        "java" -> "java"
        "kotlin" -> "kt"
        else -> "java"
    }

    private fun isController() = fileName.endsWith("Controller." + langSuffix())
    private fun isService() =
        fileName.endsWith("Service." + langSuffix()) || fileName.endsWith("ServiceImpl." + langSuffix())


    override fun initContext(
        actionType: ChatActionType,
        selectedText: String,
        file: PsiFile?,
        project: Project,
        offset: Int
    ) {
        super.initContext(actionType, selectedText, file, project, offset)
        changeListManager = ChangeListManagerImpl.getInstance(project)
        mvcContextService = MvcContextService(project)

        lang = file?.language?.displayName ?: ""
        fileName = file?.name ?: ""
    }

    init {
        val prompts = autoDevSettingsState.customEnginePrompts
        customPromptConfig = CustomPromptConfig.tryParse(prompts)
    }

    override fun displayPrompt(): String {
        return runBlocking {
            val prompt = createPrompt(selectedText)


            val finalPrompt = if (additionContext.isNotEmpty()) {
                """$additionContext
                |$selectedText""".trimMargin()
            } else {
                selectedText
            }

            return@runBlocking """$prompt:
         <pre><code>$finalPrompt</pre></code>
        """.trimMargin()
        }
    }

    override fun requestPrompt(): String {
        return runBlocking {
            val prompt = createPrompt(selectedText)

            val finalPrompt = if (additionContext.isNotEmpty()) {
                """$additionContext
                |$selectedText""".trimMargin()
            } else {
                selectedText
            }

            return@runBlocking """$prompt:
                    $finalPrompt
                """.trimMargin()
        }
    }


    private suspend fun createPrompt(selectedText: String): String {
        var prompt = action!!.instruction(lang)

        when (action!!) {
            ChatActionType.REVIEW -> {
                val codeReview = customPromptConfig?.codeReview
                if (codeReview?.instruction?.isNotEmpty() == true) {
                    prompt = codeReview.instruction
                }
            }

            ChatActionType.EXPLAIN -> {
                val autoComment = customPromptConfig?.autoComment
                if (autoComment?.instruction?.isNotEmpty() == true) {
                    prompt = autoComment.instruction
                }
            }

            ChatActionType.REFACTOR -> {
                val refactor = customPromptConfig?.refactor
                if (refactor?.instruction?.isNotEmpty() == true) {
                    prompt = refactor.instruction
                }
            }

            ChatActionType.CODE_COMPLETE -> {
                val codeComplete = customPromptConfig?.autoComplete
                if (codeComplete?.instruction?.isNotEmpty() == true) {
                    prompt = codeComplete.instruction
                }

                when {
                    isController() -> {
                        val spec = CustomPromptConfig.load().spec["controller"]
                        if (!spec.isNullOrEmpty()) {
                            additionContext = "requirements: \n$spec"
                        }
                        additionContext += mvcContextService.controllerPrompt(file)
                    }

                    isService() -> {
                        val spec = CustomPromptConfig.load().spec["service"]
                        if (!spec.isNullOrEmpty()) {
                            additionContext = "requirements: \n$spec"
                        }
                        additionContext += mvcContextService.servicePrompt(file)
                    }

                    else -> {
                        additionContext = SimilarChunksWithPaths.createQuery(file!!) ?: ""
                    }
                }
            }

            ChatActionType.WRITE_TEST -> {
                val writeTest = customPromptConfig?.writeTest
                if (writeTest?.instruction?.isNotEmpty() == true) {
                    prompt = writeTest.instruction
                }

                // todo: change to scope
                val creationContext = ChatCreationContext(ChatOrigin.ChatAction, action!!, file)
                val allContexts = ChatContextProvider.collectChatContext(project!!, creationContext)

                additionContext = allContexts
                logger.info("additionContext: $additionContext")
            }

            ChatActionType.FIX_ISSUE -> {
                addFixIssueContext(selectedText)
            }

            ChatActionType.GEN_COMMIT_MESSAGE -> {
                prepareVcsContext()
            }

            ChatActionType.CREATE_DDL -> {
                val spec = CustomPromptConfig.load().spec["ddl"]
                if (!spec.isNullOrEmpty()) {
                    additionContext = "requirements: \n$spec"
                }
                prompt = "create ddl based on the follow info"
            }

            ChatActionType.CREATE_CHANGELOG -> {
                prompt = "generate release note base on the follow commit"
            }

            ChatActionType.CHAT -> {
                prompt = ""
            }
        }

        return prompt
    }

    private fun prepareVcsContext() {
        val changes = changeListManager.changeLists.flatMap {
            it.changes
        }

//        EditorHistoryManager, after 2023.2, can use the following code
//        val commitWorkflowUi: CommitWorkflowUi = project.service()
//        val changes = commitWorkflowUi.getIncludedChanges()

        val prompting = project!!.service<VcsPrompting>()
        additionContext += prompting.computeDiff(changes)
    }

    private fun addFixIssueContext(selectedText: String) {
        val projectPath = project!!.basePath ?: ""
        runReadAction {
            val lookupFile = if (selectedText.contains(projectPath)) {
                val regex = Regex("$projectPath(.*\\.)${langSuffix()}")
                val relativePath = regex.find(selectedText)?.groupValues?.get(1) ?: ""
                val file = LocalFileSystem.getInstance().findFileByPath(projectPath + relativePath)
                file?.let {
                    val psiFile = PsiManager.getInstance(project!!).findFile(it)
                    psiFile
                }
            } else {
                null
            }

            if (lookupFile != null) {
                additionContext = lookupFile.text.toString()
            }
        }
    }

    companion object {
        val logger = logger<JvmIdeaContextPrompter>()
    }
}
