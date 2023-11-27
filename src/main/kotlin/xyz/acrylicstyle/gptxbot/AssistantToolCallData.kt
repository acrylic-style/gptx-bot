package xyz.acrylicstyle.gptxbot

data class AssistantToolCallData(
    val id: String,
    var function: Function? = null,
) {
    data class Function(
        var name: String = "",
        var arguments: String = "",
    )

    fun getAndSetFunction(): Function {
        if (function == null) function = Function()
        return function!!
    }
}
