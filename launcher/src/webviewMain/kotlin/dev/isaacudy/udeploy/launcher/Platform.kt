package dev.isaacudy.udeploy.launcher

import dev.isaacudy.udeploy.launcher.cinterop.webview.webview_create
import dev.isaacudy.udeploy.launcher.cinterop.webview.webview_destroy
import dev.isaacudy.udeploy.launcher.cinterop.webview.webview_hint_t
import dev.isaacudy.udeploy.launcher.cinterop.webview.webview_run
import dev.isaacudy.udeploy.launcher.cinterop.webview.webview_set_html
import dev.isaacudy.udeploy.launcher.cinterop.webview.webview_set_size
import dev.isaacudy.udeploy.launcher.cinterop.webview.webview_set_title
import kotlinx.cinterop.ExperimentalForeignApi

// Shared `actual fun runLauncher` for every target that has a working
// webview cinterop. The webview C API is identical across macOS,
// Linux, and Windows so the same Kotlin code compiles for each — the
// platform-specific differences live entirely in webview's static
// library (which is built per-target by the buildWebview... tasks).
//
// linuxArm64 doesn't yet have a webview build, so it falls back to
// the stub actual in linuxArm64Main/Platform.kt.
@OptIn(ExperimentalForeignApi::class)
actual fun runLauncher(args: Array<String>) {
    val w = webview_create(/* debug = */ 1, /* window = */ null)
        ?: error("webview_create returned null")
    try {
        webview_set_title(w, "udeploy launcher (debug)")
        webview_set_size(w, 600, 400, webview_hint_t.WEBVIEW_HINT_NONE)
        webview_set_html(
            w,
            """
            <!doctype html>
            <html>
              <head>
                <meta charset="utf-8">
                <style>
                  body {
                    font-family: -apple-system, system-ui, sans-serif;
                    margin: 24px;
                    color: #1a1a1a;
                    background: #faf6ee;
                  }
                  h1 { font-size: 18px; margin: 0 0 12px 0; }
                  .kicker { font-size: 11px; color: #888; letter-spacing: 0.1em; text-transform: uppercase; margin-bottom: 4px; }
                  pre { background: #f0e8d4; padding: 12px; border-radius: 6px; font-size: 12px; }
                </style>
              </head>
              <body>
                <div class="kicker">udeploy / debug</div>
                <h1>Launcher running</h1>
                <p>This window will eventually show update status,
                   download progress, and error states.</p>
                <pre>args: ${args.joinToString(" ")}</pre>
              </body>
            </html>
            """.trimIndent(),
        )
        webview_run(w)
    } finally {
        webview_destroy(w)
    }
}
