package test

import me.anno.config.DefaultConfig
import me.anno.gpu.RenderDoc
import me.anno.remsstudio.DownloadUI
import me.anno.ui.debug.TestStudio


/**
 * Test for this DownloadUI
 * */
fun main() {
    RenderDoc.disableRenderDoc()
    TestStudio.testUI2("DownloadTest") {
        DownloadUI.createUI(DefaultConfig.style)
    }
}
