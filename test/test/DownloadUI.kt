package test

import me.anno.config.DefaultConfig
import me.anno.gpu.RenderDoc
import me.anno.remsstudio.DownloadUI
import me.anno.ui.debug.TestEngine


/**
 * Test for this DownloadUI
 * */
fun main() {
    RenderDoc.disableRenderDoc()
    TestEngine.testUI2("DownloadTest") {
        DownloadUI.createUI(DefaultConfig.style)
    }
}
